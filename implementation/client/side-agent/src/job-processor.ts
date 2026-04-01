import { 
  JobHub, 
  JobResponse, 
  JobStatus, 
  JobStatus_State, 
  JobResult, 
  StepReport, 
  Telemetry, 
  Telemetry_Severity, 
  Summary, 
} from "uap-node";
import { Timestamp, Struct } from "@bufbuild/protobuf";
import { TestLogger, TestRunner } from "@hsgamer/side-engine";
import { Builder, WebDriver } from "selenium-webdriver";
import { 
  WebDriverExecutor, 
  Variables, 
  PlaybackStates, 
  CommandStates, 
} from "@seleniumhq/side-runtime";
import type { TestShape } from "@seleniumhq/side-model";
import { createClient, Client } from "@connectrpc/connect";
import { msToDuration } from "./utils";
import { TestReport } from "./types";
import { CONFIG } from "./config";

/**
 * Manages the lifecycle of a single UAP Job Execution session.
 * Encapsulates Selenium orchestration, telemetry streaming, and result reporting.
 */
export class JobProcessor {
  private outboundQueue: JobResponse[] = [];
  private resolveOutbound: (() => void) | null = null;
  private driver: WebDriver | null = null;

  constructor(
    private readonly sessionId: string,
    private readonly client: Client<typeof JobHub>,
    private readonly onDriverCreated: (driver: WebDriver) => void,
    private readonly onDriverDestroyed: (driver: WebDriver) => void
  ) {}

  /**
   * Starts the bi-directional execution stream with the Hub.
   */
  public async process() {
    try {
      const session = this.client.execute(this.sendMessages(), {
        headers: { "uap-session-id": this.sessionId },
      });
      console.log(`[Job ${this.sessionId}] Execution session established.`);

      for await (const instruction of session) {
        if (instruction.content.case === "jobInit") {
          await this.runSideTest(
            instruction.content.value.payload as any,
            instruction.content.value.runtimeEnv!
          );
        } else if (instruction.content.case === "command") {
          console.log(`[Job ${this.sessionId}] Received command: ${instruction.content.case}`);
        }
      }
    } catch (err) {
      console.error(`[Job ${this.sessionId}] Session terminated:`, err);
    } finally {
      await this.cleanup();
    }
  }

  /**
   * Pushes a status update or result back to the Hub.
   */
  private pushUpdate(response: JobResponse["response"]) {
    this.outboundQueue.push(new JobResponse({ timestamp: Timestamp.now(), response }));
    if (this.resolveOutbound) {
      this.resolveOutbound();
      this.resolveOutbound = null;
    }
  }

  /**
   * Streams telemetry (logs) back to the Hub with correct severity and source.
   */
  private streamLog(message: string, level = Telemetry_Severity.INFO) {
    this.pushUpdate({
      case: "telemetry",
      value: new Telemetry({
        message,
        timestamp: Timestamp.now(),
        level,
        source: "selenium-engine",
      }),
    });
  }

  /**
   * Generator for the outbound JobResponse stream.
   */
  private async* sendMessages(): AsyncGenerator<JobResponse> {
    while (true) {
      if (this.outboundQueue.length > 0) {
        yield this.outboundQueue.shift()!;
      } else {
        await new Promise<void>((r) => (this.resolveOutbound = r));
      }
    }
  }

  /**
   * Core execution logic: sets up the driver, runs the test, and reports results.
   */
  private async runSideTest(payload: any, runtimeEnv: Struct) {
    if (payload.type !== "selenium-side") {
      this.pushUpdate({ 
        case: "status", 
        value: new JobStatus({ state: JobStatus_State.INVALID, message: "Type mismatch: expected selenium-side" }) 
      });
      return;
    }

    const sideCommands = JSON.parse(
      payload.content.case === "rawData" 
        ? new TextDecoder().decode(payload.content.value) 
        : JSON.stringify(payload.content.value.toJson())
    );

    this.pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.ACKNOWLEDGED, message: "Initializing Selenium engine..." }) });

    const test: TestShape = { id: this.sessionId, name: "UAP Session Job", commands: sideCommands };
    const driverBuilder = new Builder();
    if (CONFIG.SELENIUM_REMOTE_URL) driverBuilder.usingServer(CONFIG.SELENIUM_REMOTE_URL);
    
    const browser = runtimeEnv.fields["selenium_browser"]?.kind.case === "stringValue" 
      ? runtimeEnv.fields["selenium_browser"].kind.value 
      : "chrome";
    driverBuilder.forBrowser(browser);

    const logger = new TestLogger();
    const sessionStartTime = new Date();

    try {
      this.driver = await driverBuilder.build();
      this.onDriverCreated(this.driver);

      const testRunner = TestRunner.createRunner(test, {
        logger: logger.createConsole(),
        variables: new Variables(),
        executor: new WebDriverExecutor({ driver: this.driver }),
      });

      // Hook into runner for isolated logging
      // Note: TestRunner doesn't expose a clean per-test event emitter for logs in this version, 
      // so we use a proxy or wrap the pushUpdate. 
      // For this cleanup, we'll prefix internal logs.
      this.streamLog(`Session started for browser: ${browser}`);

      this.pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.EXECUTING, message: "Running test steps..." }) });
      await testRunner.run();

      const report = (await testRunner.createReport(logger)) as TestReport;
      const finalState = report.state === PlaybackStates.FINISHED ? JobStatus_State.COMPLETED : JobStatus_State.FAILED;
      const sessionEndTime = new Date();

      this.pushUpdate({
        case: "result",
        value: new JobResult({
          status: new JobStatus({ state: finalState }),
          steps: report.commands.map((cmd) => {
            const stepStart = cmd.timestamp.length > 0 ? cmd.timestamp[0].timestamp : new Date();
            const stepEnd = cmd.timestamp.length > 0 ? cmd.timestamp[cmd.timestamp.length - 1].timestamp : new Date();
            return new StepReport({
              name: `${cmd.command.command} ${cmd.command.target}`,
              status: new JobStatus({ 
                state: cmd.state === CommandStates.PASSED ? JobStatus_State.COMPLETED : JobStatus_State.FAILED,
                message: cmd.timestamp.find(t => t.message)?.message
              }),
              summary: new Summary({
                startTime: Timestamp.fromDate(stepStart),
                totalDuration: msToDuration(stepEnd.getTime() - stepStart.getTime()),
                metadata: Struct.fromJson({
                  id: cmd.id,
                  command: cmd.command.command,
                  target: cmd.command.target ?? null,
                  value: cmd.command.value ?? null,
                })
              })
            });
          }),
          logs: report.logs,
          summary: new Summary({
            startTime: Timestamp.fromDate(sessionStartTime),
            totalDuration: msToDuration(sessionEndTime.getTime() - sessionStartTime.getTime()),
            metadata: Struct.fromJson({
              total_steps: report.commands.length,
              successful_steps: report.commands.filter(c => c.state === CommandStates.PASSED).length,
              browser,
            })
          })
        }),
      });

      this.pushUpdate({ case: "status", value: new JobStatus({ state: finalState }) });
    } catch (err: any) {
      this.pushUpdate({ case: "status", value: new JobStatus({ state: JobStatus_State.FAILED, message: `Execution Error: ${err.message}` }) });
    }
  }

  private async cleanup() {
    if (this.driver) {
      this.onDriverDestroyed(this.driver);
      await this.driver.quit().catch(() => {});
      this.driver = null;
    }
  }
}
