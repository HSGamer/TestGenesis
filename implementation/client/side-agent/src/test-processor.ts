import { TestHub } from "./generated/TestHub_connect.js";
import { TestResponse } from "./generated/TestResponse_pb.js";
import { TestStatus } from "./generated/TestStatus_pb.js";
import { TestState } from "./generated/TestState_pb.js";
import { TestResult } from "./generated/TestResult_pb.js";
import { StepReport } from "./generated/StepReport_pb.js";
import { Telemetry } from "./generated/Telemetry_pb.js";
import { Severity } from "./generated/Severity_pb.js";
import { Summary } from "./generated/Summary_pb.js";
import { Payload } from "./generated/Payload_pb.js";
import { Attachment } from "./generated/Attachment_pb.js";

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
import { Client } from "@connectrpc/connect";
import { createWritableIterable } from "@connectrpc/connect/protocol";
import { msToDuration, TestReport } from "./common.js";

import { CONFIG } from "./config.js";

export class TestProcessor {
  private driver: WebDriver | null = null;
  private requestIterable = createWritableIterable<TestResponse>();

  constructor(
    private readonly sessionId: string,
    private readonly client: Client<typeof TestHub>,
    private readonly onDriverCreated: (driver: WebDriver) => void,
    private readonly onDriverDestroyed: (driver: WebDriver) => void
  ) {}

  public async process() {
    try {
      const stream = this.client.execute(this.requestIterable, {
        headers: { "x-session-id": this.sessionId },
      });


      for await (const instruction of stream) {
        if (instruction.event.case === "testInit") {
          await this.executeTest(instruction.event.value);
        }
      }
    } catch (err) {
      console.error(`[Test ${this.sessionId}] Error:`, err);
    } finally {
      await this.cleanup();
    }
  }

  private pushResponse(event: TestResponse["event"]) {
    this.requestIterable.write(new TestResponse({ timestamp: Timestamp.now(), event }));
  }

  private sendLog(message: string, severity = Severity.INFO) {
    this.pushResponse({
      case: "telemetry",
      value: new Telemetry({ message, timestamp: Timestamp.now(), severity, source: "side-agent" }),
    });
  }

  private async executeTest(request: any) {
    const { testType, payloads } = request;
    const payload = payloads.find((p: any) => p.type === testType);
    
    if (!payload?.attachment) {
      this.pushResponse({ 
        case: "status", 
        value: new TestStatus({ state: TestState.INVALID, message: "Missing required test payload or attachment" }) 
      });
      return;
    }

    // Determine browser from environment config if available
    const envPayload = payloads.find((p: any) => p.type === "runtime-env");
    let browser = "chrome";
    if (envPayload?.attachment) {
      try {
        const config = JSON.parse(new TextDecoder().decode(envPayload.attachment.data));
        browser = config.browser || browser;
      } catch (e) {}
    }

    this.pushResponse({ case: "status", value: new TestStatus({ state: TestState.ACKNOWLEDGED, message: "Initializing Selenium..." }) });

    try {
      let parsedTest: Partial<TestShape>;
      try {
        parsedTest = JSON.parse(new TextDecoder().decode(payload.attachment.data));
      } catch (err: any) {
        throw new Error(`Failed to parse test payload as JSON: ${err.message}`);
      }

      if (!parsedTest || typeof parsedTest !== "object") {
        throw new Error("Parsed test payload is not a valid JSON object.");
      }
      
      if (!Array.isArray(parsedTest.commands)) {
        throw new Error("Invalid test payload: 'commands' property is missing or not an array.");
      }

      const test: TestShape = { 
        id: parsedTest.id || this.sessionId, 
        name: parsedTest.name || "UAP Execution", 
        commands: parsedTest.commands 
      };

      
      const builder = new Builder().forBrowser(browser);
      if (CONFIG.SELENIUM_REMOTE_URL) builder.usingServer(CONFIG.SELENIUM_REMOTE_URL);
      
      this.driver = await builder.build();
      this.onDriverCreated(this.driver);

      const logger = new TestLogger();
      const testRunner = TestRunner.createRunner(test, {
        logger: logger.createConsole(),
        variables: new Variables(),
        executor: new WebDriverExecutor({ driver: this.driver }),
      });

      this.sendLog(`Session started for browser: ${browser}`);
      this.pushResponse({ case: "status", value: new TestStatus({ state: TestState.RUNNING, message: "Running steps..." }) });
      
      const startTime = new Date();
      await testRunner.run();
      const endTime = new Date();
      
      const report = (await testRunner.createReport(logger)) as TestReport;
      const finalState = report.state === PlaybackStates.FINISHED ? TestState.COMPLETED : TestState.FAILED;

      this.pushResponse({
        case: "result",
        value: new TestResult({
          status: new TestStatus({ state: finalState }),
          reports: report.commands.map(cmd => new StepReport({
            name: `${cmd.command.command} ${cmd.command.target || ""}`,
            status: cmd.state === CommandStates.PASSED ? "COMPLETED" : "FAILED",
            metadata: { id: cmd.id, command: cmd.command.command },
            summary: new Summary({
              startTime: Timestamp.fromDate(cmd.timestamp[0]?.timestamp || new Date()),
              totalDuration: msToDuration((cmd.timestamp[cmd.timestamp.length-1]?.timestamp.getTime() || 0) - (cmd.timestamp[0]?.timestamp.getTime() || 0))
            })
          })),
          summary: new Summary({
            startTime: Timestamp.fromDate(startTime),
            totalDuration: msToDuration(endTime.getTime() - startTime.getTime()),
            metadata: Struct.fromJson({ total_steps: report.commands.length, browser })
          })
        })
      });

      this.pushResponse({ case: "status", value: new TestStatus({ state: finalState }) });
    } catch (err: any) {
      this.pushResponse({ case: "status", value: new TestStatus({ state: TestState.FAILED, message: `Error: ${err.message}` }) });
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
