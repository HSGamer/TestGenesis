import {create} from "@bufbuild/protobuf";
import {WritableIterable} from "@connectrpc/connect/protocol";
import {TestInit} from "./generated/index.js";
import {TestResponse, TestResponseSchema} from "./generated/index.js";
import {TestStatus} from "./generated/index.js";
import {TestResult} from "./generated/index.js";
import {TelemetrySchema} from "./generated/index.js";
import {TranslationInit} from "./generated/index.js";
import {TranslationResponse, TranslationResponseSchema} from "./generated/index.js";
import {TranslationStatus} from "./generated/index.js";
import {TranslationResult} from "./generated/index.js";
import {Severity} from "./generated/index.js";
import {timestampNow} from "./utils.js";

/**
 * Common high-level operations for any active test/translation session.
 */
export abstract class SessionContext<TInit, TResponse> {
    constructor(
        public readonly init: TInit,
        protected readonly responseIterable: WritableIterable<TResponse>
    ) {
    }
}

/**
 * Domain-specific context for Test sessions.
 */
export class TestSessionContext extends SessionContext<TestInit, TestResponse> {
    public sendStatus(status: TestStatus) {
        this.responseIterable.write(create(TestResponseSchema, {
            timestamp: timestampNow(),
            event: {case: "status", value: status}
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
            event: {case: "result", value: result}
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
            event: {case: "status", value: status}
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
            event: {case: "result", value: result}
        }));
    }
}
