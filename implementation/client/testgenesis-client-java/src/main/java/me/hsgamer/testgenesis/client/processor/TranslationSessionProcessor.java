package me.hsgamer.testgenesis.client.processor;

import me.hsgamer.testgenesis.client.context.TranslationSessionContext;

/**
 * Interface for script translation logic.
 */
public interface TranslationSessionProcessor extends BaseProcessor {
    /**
     * Processes a translation session.
     *
     * @param sessionId The session ID.
     * @param context   The session context.
     * @throws Exception if an error occurs.
     */
    void process(String sessionId, TranslationSessionContext context) throws Exception;
}
