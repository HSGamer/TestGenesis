import { createGrpcTransport } from "@connectrpc/connect-node";
import { createClient } from "@connectrpc/connect";
import {
  JobHub,
  JobListenRequest,
  JobListenResponse,
  ExecuteJobMessage,
  JobResponse,
  JobStatus,
  JobStatus_State,
  JobResult,
  StepReport,
  Telemetry,
} from "uap-node";
import { Timestamp, Struct } from "@bufbuild/protobuf";
import { parseArgs } from "node:util";
import { TestLogger, TestRunner } from "@hsgamer/side-engine";
import { Builder } from "selenium-webdriver";
import { 
  WebDriverExecutor, 
  Variables, 
  PlaybackStates, 
  PlaybackState, 
  CommandStates, 
  CommandState 
} from "@seleniumhq/side-runtime";
import type { CommandShape, TestShape } from "@seleniumhq/side-model";

// --- Types from Reference Agent ---
type PlaybackTimestamp = {
  state: PlaybackState;
  timestamp: Date;
};
type CommandTimestamp = {
  state: CommandState;
  timestamp: Date;
  message?: string;
  error?: Error;
};
type TestCommandReport = {
  id: string;
  command: CommandShape;
  state: CommandState;
  timestamp: CommandTimestamp[];
};
type TestReport = {
  id: string;
  name: string;
  state: PlaybackState;
  timestamp: PlaybackTimestamp[];
  commands: TestCommandReport[];
  logs: string[];
};

// --- Arguments ---
const { values } = parseArgs({
  args: process.argv.slice(2),
  options: {
    name: { type: "string", short: "n" },
    url: { type: "string", short: "u" },
    selenium: { type: "string", short: "s" },
  },
});

const HUB_URL = values.url || process.env.HUB_URL || "http://localhost:9090";
const CLIENT_NAME = values.name || process.env.CLIENT_NAME || "SideAgent-" + Math.random().toString(36).substring(7);
const SELENIUM_REMOTE_URL = values.selenium || process.env.SELENIUM_REMOTE_URL;

console.log(`Starting UAP Node Side Agent: ${CLIENT_NAME}`);
const transport = createGrpcTransport({ baseUrl: HUB_URL, httpVersion: "2" });
const client = createClient(JobHub, transport);

async function main() {
  let delay = 1000;
  while (true) {
    try {
      const listenRequest = new JobListenRequest({
        displayName: CLIENT_NAME,
        capabilities: [{ testType: "selenium-side" }],
      });
      for await (const directive of client.listen(listenRequest)) {
        delay = 1000;
        if (directive.content.case === "registration") {
          console.log(`[Listen] Registered: ${directive.content.value.agentId}`);
        } else if (directive.content.case === "runJob") {
          executeJob(directive.content.value.sessionId).catch(console.error);
        }
      }
    } catch (err) {
      console.error(`[Listen] Error: ${err}. Reconnecting in ${delay}ms...`);
      await new Promise((resolve) => setTimeout(resolve, delay));
      delay = Math.min(delay * 2, 60000);
    }
  }
}

async function executeJob(sessionId: string) {
  const outboundQueue: ExecuteJobMessage[] = [];
  let resolveOutbound: (() => void) | null = null;

  function pushUpdate(response: JobResponse["response"]) {
    outboundQueue.push(new ExecuteJobMessage({
      content: { case: "update", value: new JobResponse({ timestamp: Timestamp.now(), response }) }
    }));
    if (resolveOutbound) { resolveOutbound(); resolveOutbound = null; }
  }

  async function* sendMessages(): AsyncGenerator<ExecuteJobMessage> {
    yield new ExecuteJobMessage({ content: { case: "sessionId", value: sessionId } });
    while (true) {
      if (outboundQueue.length > 0) yield outboundQueue.shift()!;
      else await new Promise<void>((r) => (resolveOutbound = r));
    }
  }

  try {
    const session = client.execute(sendMessages());
    for await (const instruction of session) {
      if (instruction.content.case === "jobInit") {
        await runSideTest(sessionId, instruction.content.value.payload as any, instruction.content.value.runtimeEnv!, pushUpdate);
      }
    }
  } catch (err) { console.error(`[Job ${sessionId}] Session error:`, err); }
}

async function runSideTest(
  sessionId: string,
  payload: any,
  runtimeEnv: Struct,
  pushUpdate: (res: JobResponse["response"]) => void
) {
  if (payload.type !== "selenium-side") {
    pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.INVALID, message: "Only selenium-side supported" }) });
    return;
  }

  let sideCommands: CommandShape[];
  try {
    const rawContent = payload.content.case === "rawData" 
      ? new TextDecoder().decode(payload.content.value)
      : JSON.stringify(payload.content.value.toJson());
    sideCommands = JSON.parse(rawContent) as CommandShape[];
  } catch (e: any) {
    pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.INVALID, message: "Invalid JSON: " + e.message }) });
    return;
  }

  pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.ACKNOWLEDGED }) });

  const test: TestShape = { id: sessionId, name: "UAP Session Job", commands: sideCommands };
  const driverBuilder = new Builder();
  if (SELENIUM_REMOTE_URL) driverBuilder.usingServer(SELENIUM_REMOTE_URL);
  
  const browser = runtimeEnv.fields["selenium_browser"]?.kind.case === "stringValue" 
    ? runtimeEnv.fields["selenium_browser"].kind.value 
    : "chrome";
  driverBuilder.forBrowser(browser);
  
  const logger = new TestLogger();
  const originalLog = console.log;
  console.log = (...args: any[]) => {
    originalLog(...args);
    pushUpdate({ case: "telemetry", value: new Telemetry({ message: args.join(" ") }) });
  };

  try {
    const testRunner = TestRunner.createRunner(test, {
      logger: logger.createConsole(),
      variables: new Variables(),
      executor: new WebDriverExecutor({ driver: driverBuilder.build() }),
    });

    pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.EXECUTING }) });
    await testRunner.run();

    const report = (await testRunner.createReport(logger)) as TestReport;
    
    const finalState = report.state === PlaybackStates.FINISHED ? JobStatus_State.COMPLETED : JobStatus_State.FAILED;

    pushUpdate({
      case: "result",
      value: new JobResult({
        status: new JobStatus({ state: finalState }),
        steps: report.commands.map((cmd) => new StepReport({
          name: `${cmd.command.command} ${cmd.command.target}`,
          status: new JobStatus({ 
            state: cmd.state === CommandStates.PASSED ? JobStatus_State.COMPLETED : JobStatus_State.FAILED,
            message: cmd.timestamp.find(t => t.message)?.message
          }),
        })),
        logs: report.logs,
      }),
    });

    pushUpdate({ case: "status", value: new JobStatus({ state: finalState }) });
  } catch (err: any) {
    pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.FAILED, message: err.message }) });
  } finally {
    console.log = originalLog;
  }
}

main().catch(console.error);
