import { JobHub } from "./generated/JobHub_connect.js";
import { JobResponse } from "./generated/JobResponse_pb.js";
import { JobStatus } from "./generated/JobStatus_pb.js";
import { JobState } from "./generated/JobState_pb.js";
import { JobResult } from "./generated/JobResult_pb.js";
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
import { createClient, Client } from "@connectrpc/connect";
import { msToDuration } from "./utils.js";
import { TestReport } from "./types.js";
import { CONFIG } from "./config.js";


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
        if (instruction.event.case === "jobInit") {
          await this.runSideTest(instruction.event.value);
        } else if (instruction.event.case === "command") {
          console.log(`[Job ${this.sessionId}] Received command: ${instruction.event.case}`);
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
  private pushUpdate(event: JobResponse["event"]) {
    this.outboundQueue.push(new JobResponse({ timestamp: Timestamp.now(), event }));
    if (this.resolveOutbound) {
      this.resolveOutbound();
      this.resolveOutbound = null;
    }
  }

  /**
   * Streams telemetry (logs) back to the Hub with correct severity and source.
   */
  private streamLog(message: string, severity = Severity.INFO) {
    this.pushUpdate({
      case: "telemetry",
      value: new Telemetry({
        message,
        timestamp: Timestamp.now(),
        severity,
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
  private async runSideTest(request: any) {
    const { testType, payloads } = request;

    // 1. Find the primary project payload based on the requested testType
    const payload = payloads.find((p: any) => p.type === testType);
    if (!payload) {
      this.pushUpdate({ 
        case: "status", 
        value: new JobStatus({ 
          state: JobState.INVALID, 
          message: `No compatible '${testType}' payload found in the request` 
        }) 
      });
      return;
    }

    // 2. Discover runtime environment from payloads if provided
    const envPayload = payloads.find((p: any) => p.type === "runtime-env");
    let browser = "chrome";
    if (envPayload && envPayload.attachment) {
      try {
        const config = JSON.parse(new TextDecoder().decode(envPayload.attachment.data));
        browser = config.browser || browser;
      } catch (e) {}
    }


    // 3. Get .side content from the primary payload attachment
    const attachment = payload.attachment;

    if (!attachment) {
      this.pushUpdate({ 
        case: "status", 
        value: new JobStatus({ 
          state: JobState.INVALID, 
          message: "Compatible payload found but it has no file content (attachment)" 
        }) 
      });
      return;
    }


    let sideCommands: any[];
    try {
      const rawContent = new TextDecoder().decode(attachment.data);
      sideCommands = JSON.parse(rawContent);
    } catch (e: any) {
      this.pushUpdate({ 
        case: "status", 
        value: new JobStatus({ 
          state: JobState.INVALID, 
          message: `Failed to parse .side JSON content: ${e.message}` 
        }) 
      });
      return;
    }

    this.pushUpdate({ case: "status", value: new JobStatus({ state: JobState.ACKNOWLEDGED, message: "Initializing Selenium engine..." }) });

    const test: TestShape = { id: this.sessionId, name: "UAP Session Job", commands: sideCommands };
    const driverBuilder = new Builder();
    if (CONFIG.SELENIUM_REMOTE_URL) driverBuilder.usingServer(CONFIG.SELENIUM_REMOTE_URL);
    
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

      this.pushUpdate({ case: "status", value: new JobStatus({ state: JobState.RUNNING, message: "Running test steps..." }) });
      await testRunner.run();

      const report = (await testRunner.createReport(logger)) as TestReport;
      const finalState = report.state === PlaybackStates.FINISHED ? JobState.COMPLETED : JobState.FAILED;
      const sessionEndTime = new Date();

      this.pushUpdate({
        case: "result",
        value: new JobResult({
          status: new JobStatus({ state: finalState }),
          reports: report.commands.map((cmd) => {
            const stepStart = cmd.timestamp.length > 0 ? cmd.timestamp[0].timestamp : new Date();
            const stepEnd = cmd.timestamp.length > 0 ? cmd.timestamp[cmd.timestamp.length - 1].timestamp : new Date();
            return new StepReport({
              name: `${cmd.command.command} ${cmd.command.target}`,
              status: cmd.state === CommandStates.PASSED ? "COMPLETED" : "FAILED",
              metadata: {
                id: cmd.id,
                command: cmd.command.command,
              },
              summary: new Summary({
                startTime: Timestamp.fromDate(stepStart),
                totalDuration: msToDuration(stepEnd.getTime() - stepStart.getTime()),
              })
            });
          }),
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
      this.pushUpdate({ case: "status", value: new JobStatus({ state: JobState.FAILED, message: `Execution Error: ${err.message}` }) });
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
