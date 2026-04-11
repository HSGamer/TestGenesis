package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.core.TranslationTicketResult;
import me.hsgamer.testgenesis.uap.v1.Payload;


import java.util.List;

@ApplicationScoped
@Slf4j
public class TranslationManager {

    @Inject
    UAPService uapService;

    @Inject
    PayloadService payloadService;

    public Uni<TranslationTicketResult> startTranslation(String agentId, String type, List<Payload> sourcePayloads) {

        TranslationTicket ticket = new TranslationTicket("", type, sourcePayloads);

        return uapService.registerTranslation(agentId, ticket).onItem().invoke(result -> {
            if (result.accepted()) {
                setupAutoSaveBackground(result.session().getTicket().sessionId());
            }
        });
    }

    private void setupAutoSaveBackground(String sessionId) {
        var session = uapService.getTranslationSessions().get(sessionId);
        if (session == null) return;

        session.addResultConsumer(result -> {
            // Offload the database operations to the worker pool to ensure it has a JTA transaction context
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                log.info("Translation session {} completed with {} payloads. Auto-saving...",
                        sessionId, result.getPayloadsCount());

                for (Payload p : result.getPayloadsList()) {
                    try {
                        PayloadEntity entity = new PayloadEntity();
                        entity.fillFromProto(p, sessionId);
                        payloadService.create(entity);
                        log.info("Saved translated payload: {}", entity.getName());
                    } catch (Exception e) {
                        log.error("Failed to save translated payload for session {}", sessionId, e);
                    }
                }
            });
        });
    }
}
