package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.Agent;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.cms.core.TranslationTicketResult;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class TranslationManager {
    private final Map<String, TranslationSession> translationSessions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<SessionAcceptance>> pendingAcceptances = new ConcurrentHashMap<>();

    @Inject
    AgentManager agentManager;

    public Optional<TranslationSession> getTranslationSession(String id) {
        return Optional.ofNullable(translationSessions.get(id));
    }

    public Collection<TranslationSession> getTranslationSessions() {
        return Collections.unmodifiableCollection(translationSessions.values());
    }

    public Uni<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        Optional<Agent> agentOpt = agentManager.getAgent(agentId);
        if (agentOpt.isEmpty() || !agentOpt.get().isReady()) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not available"));
        }
        Agent agent = agentOpt.get();
        String sid = "TRN-" + UUID.randomUUID();

        return Uni.createFrom().emitter(e -> {
            pendingAcceptances.put(sid, acc -> {
                pendingAcceptances.remove(sid);
                if (!acc.getAccepted()) {
                    e.complete(new TranslationTicketResult(false, "Rejected by agent", null));
                    return;
                }

                TranslationSession s = new TranslationSession(sid, ticket);
                translationSessions.put(sid, s);

                if (agent instanceof AgentManager.AgentImpl agentImpl) {
                    agentImpl.activeSessions.add(sid);
                    s.onCompletion(() -> agentImpl.activeSessions.remove(sid));
                }

                agentManager.sendToAgent(agentId, ListenResponse.newBuilder()
                    .setSessionReady(SessionReady.newBuilder().setSessionId(sid).build())
                    .build());

                e.complete(new TranslationTicketResult(true, "Accepted", s));
            });

            SessionProposal prop = SessionProposal.newBuilder()
                .setSessionId(sid)
                .setTranslation(TranslationProposalDetails.newBuilder().setType(ticket.targetFormat()).build())
                .build();

            agentManager.sendToAgent(agentId, ListenResponse.newBuilder()
                .setSessionProposal(prop).build());
        });
    }

    public void handleAcceptance(String sessionId, SessionAcceptance acceptance) {
        Optional.ofNullable(pendingAcceptances.get(sessionId)).ifPresent(c -> c.accept(acceptance));
    }

    public Uni<TranslationTicketResult> startTranslation(String agentId, String type, List<Payload> sourcePayloads) {
        TranslationTicket ticket = new TranslationTicket(type, sourcePayloads);
        return registerTranslation(agentId, ticket).onItem().invoke(result -> {
            if (result.accepted()) {
                var session = result.session();
                session.addResultConsumer(translationResult -> {
                    log.info("Translation session {} completed with {} payloads.", session.getSessionId(), translationResult.getPayloadsCount());
                });
            }
        });
    }

    public void failSession(String sessionId, String reason) {
        Optional.ofNullable(translationSessions.remove(sessionId)).ifPresent(s -> s.updateStatus(TranslationStatus.newBuilder()
            .setState(TranslationState.TRANSLATION_STATE_FAILED)
            .setMessage(reason)
            .build()));
    }
}
