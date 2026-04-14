import {
  CapabilitySchema,
  create,
  msToDuration,
  StepReportSchema,
  StepStatus,
  SummarySchema,
  TestCapabilitySchema,
  TestResultSchema,
  TestSessionContext,
  TestSessionProcessor,
  TestState,
  TestStatusSchema,
  timestampFromDate
} from "testgenesis-client-node";

import {TestLogger, TestRunner} from "@hsgamer/side-engine";
import {Builder, WebDriver} from "selenium-webdriver";
import * as os from "os";
import {CommandStates, PlaybackStates, Variables, WebDriverExecutor,} from "@seleniumhq/side-runtime";
import type {TestShape} from "@seleniumhq/side-model";


type TestReport = ReturnType<TestLogger["createReport"]>;

/**
 * Singleton handler for Selenium-based test jobs.
 */
export class TestProcessor implements TestSessionProcessor {
    constructor(
        private readonly onDriverCreated: (driver: WebDriver) => void,
        private readonly onDriverDestroyed: (driver: WebDriver) => void,
        private readonly seleniumRemoteUrl?: string
    ) {
    }

    public getCapability() {
        return create(CapabilitySchema, {
            format: {
                case: "test",
                value: create(TestCapabilitySchema, {
                    type: "selenium-side",
                    payloads: [
                        {type: "selenium-side", isRequired: true, acceptedMimeTypes: ["application/json"]},
                        {type: "runtime-env", acceptedMimeTypes: ["application/json"]}
                    ]
                })
            }
        });
    }

    public async process(sessionId: string, context: TestSessionContext) {
        await new TestSession(
            sessionId,
            context,
            this.onDriverCreated,
            this.onDriverDestroyed,
            this.seleniumRemoteUrl
        ).execute();
    }
}

/**
 * Per-session logic for Selenium test execution.
 */
class TestSession {
    private driver: WebDriver | null = null;

    constructor(
        private readonly sessionId: string,
        private readonly context: TestSessionContext,
        private readonly onDriverCreated: (driver: WebDriver) => void,
        private readonly onDriverDestroyed: (driver: WebDriver) => void,
        private readonly seleniumRemoteUrl?: string
    ) {
    }

    public async execute() {
        try {
            const init = this.context.init;
            const {testType, payloads} = init;
            const payload = payloads.find((p: any) => p.type === testType);

            if (!payload?.attachment) {
                this.context.sendStatus(create(TestStatusSchema, {
                    state: TestState.INVALID,
                    message: "Missing required test payload or attachment"
                }));
                return;
            }

            // Determine browser from environment config if available
            const envPayload = payloads.find((p: any) => p.type === "runtime-env");
            let browser = "chrome";
            if (envPayload?.attachment) {
                try {
                    const config = JSON.parse(new TextDecoder().decode(envPayload.attachment.data));
                    browser = config.browser || browser;
                } catch (e) {
                }
            }

            this.context.sendStatus(create(TestStatusSchema, {
                state: TestState.ACKNOWLEDGED,
                message: "Initializing Selenium..."
            }));

            let parsedTest: Partial<TestShape>;
            try {
                parsedTest = JSON.parse(new TextDecoder().decode(payload.attachment.data));
            } catch (err: any) {
                throw new Error(`Failed to parse test payload as JSON: ${err.message}`);
            }

            const test: TestShape = {
                id: parsedTest.id || this.sessionId,
                name: parsedTest.name || "UAP Execution",
                commands: parsedTest.commands || []
            };

            if (!test.commands.length) {
                throw new Error("Invalid test payload: 'commands' property is missing or empty.");
            }

            const builder = new Builder().forBrowser(browser);
            if (this.seleniumRemoteUrl) builder.usingServer(this.seleniumRemoteUrl);

            this.driver = await builder.build();
            this.onDriverCreated(this.driver);

            const logger = new TestLogger();
            const testRunner = TestRunner.createRunner(test, {
                logger: logger.createConsole(),
                variables: new Variables(),
                executor: new WebDriverExecutor({driver: this.driver}),
            });
            logger.bind(testRunner);

            this.context.sendTelemetry(`Session started for browser: ${browser}`);
            this.context.sendStatus(create(TestStatusSchema, {
                state: TestState.RUNNING,
                message: "Running steps..."
            }));

            const startTime = new Date();
            await testRunner.run();
            const endTime = new Date();

            const report = (await testRunner.createReport(logger)) as TestReport;
            const finalState = report.state === PlaybackStates.FINISHED ? TestState.COMPLETED : TestState.FAILED;

            this.context.sendResult(create(TestResultSchema, {
                status: create(TestStatusSchema, {state: finalState}),
                reports: report.commands.map(cmd => create(StepReportSchema, {
                    name: `${cmd.command.command} ${cmd.command.target || ""}`,
                    status: cmd.state === CommandStates.PASSED ? StepStatus.PASSED : StepStatus.FAILED,
                    summary: create(SummarySchema, {
                        startTime: timestampFromDate(cmd.timestamp[0]?.timestamp || new Date()),
                        totalDuration: msToDuration(
                            cmd.timestamp.length >= 2
                                ? (cmd.timestamp[cmd.timestamp.length - 1]?.timestamp.getTime() || 0) - (cmd.timestamp[0]?.timestamp.getTime() || 0)
                                : 0
                        ),
                        metadata: {
                            message: cmd.timestamp.find(t => t.message || t.error)?.message ||
                                cmd.timestamp.find(t => t.error)?.error?.message || ""
                        }
                    })
                })),
                summary: create(SummarySchema, {
                    startTime: timestampFromDate(report.timestamp[0]?.timestamp || startTime),
                    totalDuration: msToDuration(
                        report.timestamp.length >= 2
                            ? (report.timestamp[report.timestamp.length - 1]?.timestamp.getTime() || endTime.getTime()) -
                            (report.timestamp[0]?.timestamp.getTime() || startTime.getTime())
                            : endTime.getTime() - startTime.getTime()
                    ),
                    metadata: {
                        total_steps: report.commands.length,
                        browser,
                        execute_duration: endTime.getTime() - startTime.getTime(),
                        os_platform: os.platform(),
                        os_release: os.release(),
                        os_arch: os.arch(),
                        cpu_model: os.cpus()[0]?.model || "unknown",
                        cpu_count: os.cpus().length,
                        memory_total_gb: Math.round(os.totalmem() / (1024 ** 3))
                    }
                })
            }));

            this.context.sendStatus(create(TestStatusSchema, {state: finalState}));
        } catch (err: any) {
            console.error(`[Test ${this.sessionId}] Error:`, err);
            this.context.sendStatus(create(TestStatusSchema, {
                state: TestState.FAILED,
                message: `Error: ${err.message}`
            }));
        } finally {
            await this.cleanup();
        }
    }

    private async cleanup() {
        if (this.driver) {
            this.onDriverDestroyed(this.driver);
            await this.driver.quit().catch(() => {
            });
            this.driver = null;
        }
    }
}
