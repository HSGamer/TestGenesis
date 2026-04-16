import {
    create,
    testCapability,
    TestResultSchema,
    TestSessionContext,
    TestSessionProcessor,
    TestState,
    TestStatusSchema,
    timestampNow,
    timestampFromDate,
    msToDuration,
    cleanObject,
    Severity,
    StepReport,
    StepReportSchema,
    StepStatus,
    SummarySchema
} from "testgenesis-client-node";

import puppeteer from "puppeteer";
import * as os from "os";
import {createRunner, parse, PuppeteerRunnerExtension, Step, UserFlow} from "@puppeteer/replay";

class TestGenesisRunnerExtension extends PuppeteerRunnerExtension {
    public reports: StepReport[] = [];
    private stepStartTime: number = 0;

    constructor(browser: puppeteer.Browser, page: puppeteer.Page, private context: TestSessionContext) {
        super(browser, page);
    }

    private getStepDetail(step: Step): string {
        const parts: string[] = [step.type];
        if ("url" in step) parts.push(`(${step.url})`);
        if ("selector" in step) {
            const selector = Array.isArray(step.selector) ? step.selector[0] : step.selector;
            parts.push(`on ${selector}`);
        }
        if ("value" in step) parts.push(`with "${step.value}"`);
        return parts.join(" ");
    }

    async beforeEachStep(step: Step, flow: UserFlow) {
        if (super.beforeEachStep) await super.beforeEachStep(step, flow);
        this.stepStartTime = Date.now();
        const detail = this.getStepDetail(step);
        await this.context.sendTelemetry(`[Step Start] ${detail}`, Severity.INFO);
    }

    async afterEachStep(step: Step, flow: UserFlow) {
        if (super.afterEachStep) await super.afterEachStep(step, flow);
        const duration = Date.now() - this.stepStartTime;
        const detail = this.getStepDetail(step);

        const report = create(StepReportSchema, {
            name: detail,
            status: StepStatus.PASSED,
            summary: create(SummarySchema, {
                startTime: timestampFromDate(new Date(this.stepStartTime)),
                totalDuration: msToDuration(duration),
                metadata: cleanObject({
                    type: step.type,
                    selector: "selector" in step ? step.selector : undefined,
                    value: "value" in step ? step.value : undefined,
                    url: "url" in step ? step.url : undefined
                })
            })
        });

        this.reports.push(report);
        await this.context.sendTelemetry(`[Step End] ${step.type} complete (${duration}ms).`, Severity.INFO);
    }
}

export class PuppeteerReplayTestProcessor implements TestSessionProcessor {
    public getCapability() {
        return testCapability({
            type: "puppeteer-replay",
            payloads: [
                {type: "chrome-devtools-recorder", isRequired: true, acceptedMimeTypes: ["application/json"]}
            ]
        });
    }

    public async process(sessionId: string, context: TestSessionContext) {
        console.log(`[PuppeteerReplay] Processing session: ${sessionId}`);

        // 1. Initial Status
        await context.sendStatus(create(TestStatusSchema, {
            state: TestState.ACKNOWLEDGED,
            message: "Extracting recording..."
        }));

        const startTime = new Date();
        let browser: puppeteer.Browser | null = null;

        try {
            // 2. Extract Recording
            const payload = context.init.payloads.find(p => p.type === "chrome-devtools-recorder");
            if (!payload) {
                throw new Error("Missing 'chrome-devtools-recorder' payload.");
            }
            if (!payload.attachment) {
                throw new Error("Payload 'chrome-devtools-recorder' is missing an attachment.");
            }

            const recordingText = new TextDecoder().decode(payload.attachment.data);
            const recordingJson = JSON.parse(recordingText);
            const recording = parse(recordingJson);

            await context.sendStatus(create(TestStatusSchema, {
                state: TestState.RUNNING,
                message: "Launching browser and starting replay..."
            }));

            // 3. Execution
            browser = await puppeteer.launch({
                headless: true, // Reverting to headless for standard agent behavior
                args: ["--no-sandbox", "--disable-setuid-sandbox"]
            });

            const page = await browser.newPage();
            const extension = new TestGenesisRunnerExtension(browser, page, context);
            const runner = await createRunner(recording, extension);

            const browserVersion = await browser.version();

            await context.sendTelemetry(`Started Puppeteer Replay runner on ${browserVersion}.`, Severity.INFO);
            await runner.run();
            await context.sendTelemetry("Puppeteer Replay runner finished successfully.", Severity.INFO);

            const endTime = new Date();
            const duration = endTime.getTime() - startTime.getTime();

            // 4. Send Result
            await context.sendResult(create(TestResultSchema, {
                status: create(TestStatusSchema, {
                    state: TestState.COMPLETED,
                    message: "Replay finished successfully"
                }),
                reports: extension.reports,
                summary: create(SummarySchema, {
                    startTime: timestampNow(),
                    totalDuration: msToDuration(duration),
                    metadata: cleanObject({
                        processor: "PuppeteerReplayTestProcessor",
                        recording_title: recording.title,
                        total_steps: extension.reports.length,
                        browser_name: "chrome",
                        browser_version: browserVersion,
                        execute_duration: duration,
                        os_platform: os.platform(),
                        os_release: os.release(),
                        os_arch: os.arch(),
                        cpu_model: os.cpus()[0]?.model,
                        cpu_count: os.cpus().length,
                        memory_total_gb: Math.round(os.totalmem() / (1024 ** 3))
                    })
                })
            }));

        } catch (err: any) {
            console.error(`[PuppeteerReplay Error] ${err.message}`);
            await context.sendResult(create(TestResultSchema, {
                status: create(TestStatusSchema, {
                    state: TestState.FAILED,
                    message: `Replay failed: ${err.message}`
                }),
                summary: create(SummarySchema, {
                    startTime: timestampNow(),
                    metadata: cleanObject({
                        error: err.message,
                        stack: err.stack
                    })
                })
            }));
        } finally {
            if (browser) {
                await browser.close().catch(e => console.error("[PuppeteerReplay] Error closing browser:", e));
            }
            await context.sendStatus(create(TestStatusSchema, {
                state: TestState.COMPLETED,
                message: "Execution cycle finished."
            }));
        }
    }
}
