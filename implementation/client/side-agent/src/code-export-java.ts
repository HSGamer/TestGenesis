import {TranslationProcessor} from "./translation-processor.js";
import {
    AttachmentSchema,
    Capability,
    create,
    PayloadSchema,
    translationCapability,
    TranslationResultSchema,
    TranslationSessionContext,
    TranslationState,
    TranslationStatusSchema
} from "testgenesis-client-node";
import _javaExporter from "@seleniumhq/code-export-java-junit";
import {CommandShape, ProjectShape, TestShape} from "@seleniumhq/side-model";
import {LanguageEmitter} from "@seleniumhq/side-code-export";

const javaExporter = ((_javaExporter as any).default || _javaExporter) as LanguageEmitter;

export default class JavaCodeExportProcessor extends TranslationProcessor {
    getCapability(): Capability {
        return translationCapability({
            type: "selenium-ide-to-java-junit",
            sourcePayloads: [
                {
                    type: "selenium-side",
                    isRequired: true,
                    acceptedMimeTypes: ["application/json"]
                },
                {
                    type: "selenium-variable",
                    isRepeatable: true,
                    acceptedMimeTypes: ["application/json"]
                }
            ],
            targetPayloads: [{
                type: "selenium-junit",
                isRequired: true,
                isRepeatable: true,
                acceptedMimeTypes: ["text/x-java-source"]
            }]
        });
    }

    async process(sessionId: string, context: TranslationSessionContext) {
        const init = context.init;
        const payloads = init.payloads;
        const payload = payloads.find((p: any) => p.type === "selenium-side");

        if (!payload?.attachment) {
            await context.sendStatus(create(TranslationStatusSchema, {
                state: TranslationState.INVALID,
                message: "Missing required test payload or attachment"
            }));
            return;
        }

        let parsedTest: Partial<TestShape>;
        try {
            parsedTest = JSON.parse(new TextDecoder().decode(payload.attachment.data));
        } catch (err: any) {
            throw new Error(`Failed to parse test payload as JSON: ${err.message}`);
        }

        let variableCommands: CommandShape[] = [];
        const variablePayloads = payloads.filter((p: any) => p.type === "selenium-variable");
        for (const variablePayload of variablePayloads) {
            if (variablePayload.attachment) {
                try {
                    const variableObject = JSON.parse(new TextDecoder().decode(variablePayload.attachment.data));
                    for (const [key, value] of Object.entries(variableObject)) {
                        variableCommands.push({
                            id: "x-variable-" + variableCommands.length,
                            command: "store",
                            target: String(value),
                            value: key,
                        })
                    }
                } catch (e) {
                }
            }
        }

        const test: TestShape = {
            id: parsedTest.id || sessionId,
            name: (parsedTest.name || "UAP_Execution").replace(/[^a-zA-Z0-0_]/g, "_"),
            commands: [
                ...variableCommands,
                ...(parsedTest.commands || []).filter(cmd => !cmd.comment?.includes("#LOCAL_ONLY#"))
            ]
        };

        const project: ProjectShape = {
            id: test.id,
            name: "UAP Project",
            url: "",
            tests: [test],
            suites: [],
            urls: [],
            plugins: [],
            version: "2.0",
            snapshot: {
                tests: [],
                dependencies: {},
                jest: { extraGlobals: [] }
            }
        };

        await context.sendStatus(create(TranslationStatusSchema, {
            state: TranslationState.PROCESSING,
            message: "Exporting to Java JUnit..."
        }));

        const { filename, body } = await javaExporter.emit.test({
            test,
            tests: [test],
            baseUrl: "",
            project,
            enableOriginTracing: false,
            beforeEachOptions: undefined,
            enableDescriptionAsComment: false
        });

        await context.sendResult(create(TranslationResultSchema, {
            status: create(TranslationStatusSchema, {state: TranslationState.COMPLETED}),
            payloads: [
                create(PayloadSchema, {
                    type: "selenium-junit",
                    attachment: create(AttachmentSchema, {
                        name: filename,
                        mimeType: "text/x-java-source",
                        data: new TextEncoder().encode(body)
                    })
                })
            ]
        }));

        await context.sendStatus(create(TranslationStatusSchema, {
            state: TranslationState.COMPLETED,
            message: `Successfully exported to ${filename}`
        }));
    }
}