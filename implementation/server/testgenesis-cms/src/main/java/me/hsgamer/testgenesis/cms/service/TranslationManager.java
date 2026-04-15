package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.cms.core.TranslationTicketResult;
import me.hsgamer.testgenesis.uap.v1.Payload;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;

@ApplicationScoped
@Slf4j
public class TranslationManager {

    @Inject
    UAPService uapService;

    @Inject
    PayloadService payloadService;

    @Inject
    ManagedExecutor managedExecutor;

    public Uni<TranslationTicketResult> startTranslation(String agentId, String type, List<Payload> sourcePayloads) {
        TranslationTicket ticket = new TranslationTicket(type, sourcePayloads);
        return uapService.registerTranslation(agentId, ticket).onItem().invoke(result -> {
            if (result.accepted()) {
                var session = result.session();
                session.addResultConsumer(translationResult -> {
                    log.info("Translation session {} completed with {} payloads.", session.getSessionId(), translationResult.getPayloadsCount());
                });
            }
        });
    }
}
