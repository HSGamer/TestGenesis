import { 
  TranslationSessionProcessor,
  TranslationSessionContext,
  TranslationStatusSchema,
  TranslationState,
  TranslationResultSchema,
  PayloadSchema,
  AttachmentSchema,
  CapabilitySchema,
  TranslationCapabilitySchema,
  create
} from "testgenesis-client-node";

import type { ProjectShape } from "@seleniumhq/side-model";

/**
 * Singleton handler for SIDE project translation jobs.
 */
export class TranslationProcessor implements TranslationSessionProcessor {
  public getCapability() {
    return create(CapabilitySchema, {
      format: {
        case: "translation",
        value: create(TranslationCapabilitySchema, {
          type: "selenium-ide-project-to-test",
          sourcePayloads: [{ type: "selenium-side-project", isRequired: true, acceptedMimeTypes: ["application/octet-stream"] }],
          targetPayloads: [{ type: "selenium-side", isRequired: true, isRepeatable: true, acceptedMimeTypes: ["application/json"] }]
        })
      }
    });
  }

  public async process(sessionId: string, context: TranslationSessionContext) {
    try {
      const init = context.init;
      context.sendStatus(create(TranslationStatusSchema, { 
        state: TranslationState.ACKNOWLEDGED, 
        message: "Starting SIDE project decomposition..." 
      }));

      const sourcePayload = init.payloads[0];
      if (!sourcePayload?.attachment) {
        context.sendStatus(create(TranslationStatusSchema, { 
          state: TranslationState.FAILED, 
          message: "No source SIDE project attachment found" 
        }));
        return;
      }

      const project: ProjectShape = JSON.parse(new TextDecoder().decode(sourcePayload.attachment.data));
      context.sendTelemetry(`Parsing project: ${project.name}`);
      context.sendStatus(create(TranslationStatusSchema, { 
        state: TranslationState.PROCESSING, 
        message: `Extracting ${project.tests.length} tests...` 
      }));

      const results = project.tests.map(test => {
        context.sendTelemetry(`Extracted test: ${test.name}`);
        return create(PayloadSchema, {
          type: "selenium-side",
          attachment: create(AttachmentSchema, {
            name: `${test.name}.json`,
            mimeType: "application/json",
            data: new TextEncoder().encode(JSON.stringify(test))
          })
        });
      });

      context.sendResult(create(TranslationResultSchema, {
        status: create(TranslationStatusSchema, { state: TranslationState.COMPLETED }),
        payloads: results
      }));

      context.sendStatus(create(TranslationStatusSchema, { 
        state: TranslationState.COMPLETED, 
        message: `Successfully split into ${results.length} tests.` 
      }));

    } catch (err: any) {
      console.error(`[Translate ${sessionId}] Error:`, err);
      context.sendStatus(create(TranslationStatusSchema, { 
        state: TranslationState.FAILED, 
        message: `Translation Error: ${err.message}` 
      }));
    }
  }
}
