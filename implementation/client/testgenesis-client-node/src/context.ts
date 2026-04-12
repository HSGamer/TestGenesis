import { create } from "@bufbuild/protobuf";
import { WritableIterable } from "@connectrpc/connect/protocol";
import { TestInit } from "./generated/TestInit_pb.js";
import { TestResponse, TestResponseSchema } from "./generated/TestResponse_pb.js";
import { TestStatus } from "./generated/TestStatus_pb.js";
import { TestResult } from "./generated/TestResult_pb.js";
import { TelemetrySchema } from "./generated/Telemetry_pb.js";
import { TranslationInit } from "./generated/TranslationInit_pb.js";
import { TranslationResponse, TranslationResponseSchema } from "./generated/TranslationResponse_pb.js";
import { TranslationStatus } from "./generated/TranslationStatus_pb.js";
import { TranslationResult } from "./generated/TranslationResult_pb.js";
import { Severity } from "./generated/Severity_pb.js";
import { timestampNow } from "./utils.js";

/**
 * Common high-level operations for any active test/translation session.
 */
export abstract class SessionContext<TInit, TResponse> {
  constructor(
    public readonly init: TInit,
    protected readonly responseIterable: WritableIterable<TResponse>
  ) {}
}

/**
 * Domain-specific context for Test sessions.
 */
export class TestSessionContext extends SessionContext<TestInit, TestResponse> {
  public sendStatus(status: TestStatus) {
    this.responseIterable.write(create(TestResponseSchema, {
      timestamp: timestampNow(),
      event: { case: "status", value: status }
    }));
  }

  public sendTelemetry(message: string, severity = Severity.INFO) {
    this.responseIterable.write(create(TestResponseSchema, {
      timestamp: timestampNow(),
      event: {
        case: "telemetry",
        value: create(TelemetrySchema, { 
          message, 
          timestamp: timestampNow(), 
          severity, 
          source: "agent" 
        })
      }
    }));
  }

  public sendResult(result: TestResult) {
    this.responseIterable.write(create(TestResponseSchema, {
      timestamp: timestampNow(),
      event: { case: "result", value: result }
    }));
  }
}

/**
 * Domain-specific context for Translation sessions.
 */
export class TranslationSessionContext extends SessionContext<TranslationInit, TranslationResponse> {
  public sendStatus(status: TranslationStatus) {
    this.responseIterable.write(create(TranslationResponseSchema, {
      timestamp: timestampNow(),
      event: { case: "status", value: status }
    }));
  }

  public sendTelemetry(message: string, severity = Severity.INFO) {
    this.responseIterable.write(create(TranslationResponseSchema, {
      timestamp: timestampNow(),
      event: {
        case: "telemetry",
        value: create(TelemetrySchema, { 
          message, 
          timestamp: timestampNow(), 
          severity, 
          source: "translator" 
        })
      }
    }));
  }

  public sendResult(result: TranslationResult) {
    this.responseIterable.write(create(TranslationResponseSchema, {
      timestamp: timestampNow(),
      event: { case: "result", value: result }
    }));
  }
}
