import { TranslationHub } from "./generated/TranslationHub_connect.js";
import { TranslationResponse } from "./generated/TranslationResponse_pb.js";
import { TranslationStatus } from "./generated/TranslationStatus_pb.js";
import { TranslationState } from "./generated/TranslationState_pb.js";
import { TranslationResult } from "./generated/TranslationResult_pb.js";
import { Telemetry } from "./generated/Telemetry_pb.js";
import { Severity } from "./generated/Severity_pb.js";
import { Payload } from "./generated/Payload_pb.js";
import { Attachment } from "./generated/Attachment_pb.js";

import { Timestamp } from "@bufbuild/protobuf";
import { Client } from "@connectrpc/connect";
import { createWritableIterable } from "@connectrpc/connect/protocol";
import type { ProjectShape } from "@seleniumhq/side-model";

export class TranslationProcessor {
  private requestIterable = createWritableIterable<TranslationResponse>();

  constructor(
    private readonly sessionId: string,
    private readonly client: Client<typeof TranslationHub>
  ) {}

  public async process() {
    try {
      const stream = this.client.translate(this.requestIterable, {
        headers: { "x-session-id": this.sessionId },
      });


      for await (const instruction of stream) {
        // The first and only instruction in this UAP version is the translation init
        await this.translateSideProject(instruction);
      }
    } catch (err) {
      console.error(`[Translate ${this.sessionId}] Error:`, err);
    }
  }

  private pushResponse(event: TranslationResponse["event"]) {
    this.requestIterable.write(new TranslationResponse({ timestamp: Timestamp.now(), event }));
  }

  private sendLog(message: string, severity = Severity.INFO) {
    this.pushResponse({
      case: "telemetry",
      value: new Telemetry({ message, timestamp: Timestamp.now(), severity, source: "side-translator" }),
    });
  }

  private async translateSideProject(init: any) {
    this.pushResponse({ 
      case: "status", 
      value: new TranslationStatus({ state: TranslationState.ACKNOWLEDGED, message: "Starting SIDE project decomposition..." }) 
    });

    const sourcePayload = init.payloads[0];
    if (!sourcePayload?.attachment) {
      this.pushResponse({ 
        case: "status", 
        value: new TranslationStatus({ state: TranslationState.FAILED, message: "No source SIDE project attachment found" }) 
      });
      return;
    }

    try {
      const project: ProjectShape = JSON.parse(new TextDecoder().decode(sourcePayload.attachment.data));
      this.sendLog(`Parsing project: ${project.name}`);
      this.pushResponse({ 
        case: "status", 
        value: new TranslationStatus({ state: TranslationState.PROCESSING, message: `Extracting ${project.tests.length} tests...` }) 
      });

      const results: Payload[] = project.tests.map(test => {
        this.sendLog(`Extracted test: ${test.name}`);
        return new Payload({
          type: "selenium-side",
          attachment: new Attachment({
            name: `${test.name}.json`,
            mimeType: "application/json",
            data: new TextEncoder().encode(JSON.stringify(test))
          })
        });
      });

      this.pushResponse({
        case: "result",
        value: new TranslationResult({
          status: new TranslationStatus({ state: TranslationState.COMPLETED }),
          payloads: results
        })
      });

      this.pushResponse({ 
        case: "status", 
        value: new TranslationStatus({ state: TranslationState.COMPLETED, message: `Successfully split into ${results.length} tests.` }) 
      });

    } catch (err: any) {
      this.pushResponse({ 
        case: "status", 
        value: new TranslationStatus({ state: TranslationState.FAILED, message: `Translation Error: ${err.message}` }) 
      });
    }
  }
}

