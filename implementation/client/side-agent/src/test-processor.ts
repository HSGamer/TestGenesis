import {
    Attachment,
    AttachmentSchema,
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
import * as selenium from "selenium-webdriver";
const seleniumVersion = (selenium as any).version as string | undefined;
import * as os from "os";
import {CommandStates, PlaybackStates, Variables, WebDriverExecutor,} from "@seleniumhq/side-runtime";
import type {TestShape} from "@seleniumhq/side-model";
import {WebDriverExecutorConstructorArgs} from "@seleniumhq/side-runtime/dist/webdriver";


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
                        {type: "selenium-variable", isRepeatable: true, acceptedMimeTypes: ["application/json"]},
                        {type: "selenium-config", acceptedMimeTypes: ["application/json"]}
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
            const {payloads} = init;
            const payload = payloads.find((p: any) => p.type === "selenium-side");

            if (!payload?.attachment) {
                await this.context.sendStatus(create(TestStatusSchema, {
                    state: TestState.INVALID,
                    message: "Missing required test payload or attachment"
                }));
                return;
            }

            const variables = new Variables();
            const variablePayloads = payloads.filter((p: any) => p.type === "selenium-variable");
            for (const variablePayload of variablePayloads) {
                if (variablePayload.attachment) {
                    try {
                        const variableObject = JSON.parse(new TextDecoder().decode(variablePayload.attachment.data));
                        for (const [key, value] of Object.entries(variableObject)) {
                            variables.set(key, value);
                        }
                    } catch (e) {
                    }
                }
            }

            let browser = "chrome";
            let takeScreenshot = false;
            const configPayload = payloads.find((p: any) => p.type === "selenium-config");
            if (configPayload?.attachment) {
                try {
                    const config = JSON.parse(new TextDecoder().decode(configPayload.attachment.data));
                    browser = config.browser || browser;
                    takeScreenshot = config.takeScreenshot || takeScreenshot;
                } catch (e) {
                }
            }

            await this.context.sendStatus(create(TestStatusSchema, {
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
                commands: (parsedTest.commands || []).filter(cmd => !cmd.comment?.includes("#LOCAL_ONLY#"))
            };

            if (!test.commands.length) {
                throw new Error("Invalid test payload: 'commands' property is missing or empty.");
            }

            const builder = new Builder().forBrowser(browser);
            if (this.seleniumRemoteUrl) builder.usingServer(this.seleniumRemoteUrl);

            this.driver = await builder.build();
            this.onDriverCreated(this.driver);

            const capabilities = await this.driver.getCapabilities();

            let attachments: Attachment[] = [];
            const screenshotMap = new Map<string, string>();
            let webDriverExecutorArgs: WebDriverExecutorConstructorArgs = {
                driver: this.driver,
                hooks: {
                    onAfterCommand: async (hook) => {
                        if (takeScreenshot) {
                            const data = await this.driver!.takeScreenshot();
                            const name = `screenshot-${hook.command.id}.png`;
                            attachments.push(create(AttachmentSchema, {
                                name,
                                mimeType: "image/png",
                                data: Buffer.from(data, 'base64')
                            }));
                            screenshotMap.set(hook.command.id, name);
                        }
                    }
                }
            };

            const logger = new TestLogger();
            const testRunner = TestRunner.createRunner(test, {
                logger: logger.createConsole(),
                variables: variables,
                executor: new WebDriverExecutor(webDriverExecutorArgs)
            });
            logger.bind(testRunner);

            await this.context.sendTelemetry(`Session started for browser: ${browser}`);
            await this.context.sendStatus(create(TestStatusSchema, {
                state: TestState.RUNNING,
                message: "Running steps..."
            }));

            const startTime = new Date();
            await testRunner.run();
            const endTime = new Date();

            const report = testRunner.createReport(logger) as TestReport;
            const finalState = report.state === PlaybackStates.FINISHED ? TestState.COMPLETED : TestState.FAILED;

            await this.context.sendResult(create(TestResultSchema, {
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
                            timestamp: cmd.timestamp.map(t => ({
                                timestamp: t.timestamp.toISOString(),
                                state: String(t.state),
                                message: t.message || "",
                                error: t.error ? {
                                    message: t.error.message,
                                    stack: t.error.stack || ""
                                } : null
                            })),
                            screenshot: screenshotMap.get(cmd.id) || ""
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
                        selenium_webdriver_version: seleniumVersion || "unknown",
                        browser_name: capabilities.getBrowserName() || "unknown",
                        browser_version: capabilities.getBrowserVersion() || "unknown",
                        platform_name: capabilities.getPlatform() || "unknown",
                        execute_duration: endTime.getTime() - startTime.getTime(),
                        os_platform: os.platform(),
                        os_release: os.release(),
                        os_arch: os.arch(),
                        cpu_model: os.cpus()[0]?.model || "unknown",
                        cpu_count: os.cpus().length,
                        memory_total_gb: Math.round(os.totalmem() / (1024 ** 3))
                    }
                }),
                attachments
            }));

            await this.context.sendStatus(create(TestStatusSchema, {state: finalState}));
        } catch (err: any) {
            console.error(`[Test ${this.sessionId}] Error:`, err);
            await this.context.sendStatus(create(TestStatusSchema, {
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
