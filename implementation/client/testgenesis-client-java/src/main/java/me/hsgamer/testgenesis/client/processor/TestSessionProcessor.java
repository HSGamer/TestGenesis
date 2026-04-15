package me.hsgamer.testgenesis.client.processor;

import me.hsgamer.testgenesis.client.context.TestSessionContext;

/**
 * Interface for test execution logic.
 */
public interface TestSessionProcessor extends BaseProcessor {
    /**
     * Processes a test session.
     *
     * @param sessionId The session ID.
     * @param context   The session context.
     * @throws Exception if an error occurs.
     */
    void process(String sessionId, TestSessionContext context) throws Exception;
}
