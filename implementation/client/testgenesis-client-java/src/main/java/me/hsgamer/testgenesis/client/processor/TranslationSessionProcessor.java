package me.hsgamer.testgenesis.client.processor;

import me.hsgamer.testgenesis.client.context.TranslationSessionContext;
import me.hsgamer.testgenesis.uap.v1.Capability;
import me.hsgamer.testgenesis.uap.v1.TranslationCapability;

/**
 * Interface for script translation logic.
 */
public interface TranslationSessionProcessor extends BaseProcessor {
    @Override
    default Capability getCapability() {
        return Capability.newBuilder()
                .setTranslation(getTranslationCapability())
                .build();
    }

    /**
     * Gets the specific translation capability.
     *
     * @return The translation capability.
     */
    TranslationCapability getTranslationCapability();

    /**
     * Processes a translation session.
     *
     * @param sessionId The session ID.
     * @param context   The session context.
     * @throws Exception if an error occurs.
     */
    void process(String sessionId, TranslationSessionContext context) throws Exception;
}
