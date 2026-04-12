import { Capability } from "./generated/Capability_pb.js";
import { TestSessionContext, TranslationSessionContext } from "./context.js";

/**
 * Base for all specialized processors.
 */
export interface BaseProcessor {
  getCapability(): Capability;
}

/**
 * Interface for test execution logic.
 */
export interface TestSessionProcessor extends BaseProcessor {
  process(sessionId: string, context: TestSessionContext): Promise<void>;
}

/**
 * Interface for script translation logic.
 */
export interface TranslationSessionProcessor extends BaseProcessor {
  process(sessionId: string, context: TranslationSessionContext): Promise<void>;
}

// Keep the internal types for the Agent to use
export type ProcessorType = "test" | "translation";
export type AnyProcessor = TestSessionProcessor | TranslationSessionProcessor;
