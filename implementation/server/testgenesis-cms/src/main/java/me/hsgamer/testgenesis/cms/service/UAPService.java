package me.hsgamer.testgenesis.cms.service;

import io.grpc.Context;
import io.grpc.Metadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.*;
import me.hsgamer.testgenesis.cms.core.impl.DefaultJobSession;
import me.hsgamer.testgenesis.cms.core.impl.DefaultTranslationSession;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class UAPService {
    public static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    public static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    public static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Consumer<JobAcceptance>> pendingJobAcceptances = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Consumer<TranslationAcceptance>> pendingTranslationAcceptances = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, DefaultJobSession> jobSessions = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, DefaultTranslationSession> translationSessions = new ConcurrentHashMap<>();

    public Map<String, Agent> getAgents() {
        return Collections.unmodifiableMap(agents);
    }

    public Map<String, AgentImpl> getInternalAgents() {
        return agents;
    }

    public Uni<JobTicketResult> registerJob(String agentId, JobTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "JOB-" + UUID.randomUUID();

        return Uni.createFrom().emitter(emitter -> {
            pendingJobAcceptances.put(sessionId, acceptance -> {
                pendingJobAcceptances.remove(sessionId);
                if (acceptance.getAccepted()) {
                    DefaultJobSession session = new DefaultJobSession(ticket);
                    jobSessions.put(sessionId, session);
                    agent.activeSessionIds().add(sessionId);
                    emitter.complete(new JobTicketResult(true, acceptance.getReason(), session));
                } else {
                    emitter.complete(new JobTicketResult(false, acceptance.getReason(), null));
                }
            });

            agent.emitResponse(ListenResponse.newBuilder()
                    .setJobProposal(JobProposal.newBuilder()
                            .setSessionId(sessionId)
                            .setTestType(ticket.testType()))
                    .build());
        });
    }

    public Uni<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "TRN-" + UUID.randomUUID();

        return Uni.createFrom().emitter(emitter -> {
            pendingTranslationAcceptances.put(sessionId, acceptance -> {
                pendingTranslationAcceptances.remove(sessionId);
                if (acceptance.getAccepted()) {
                    DefaultTranslationSession session = new DefaultTranslationSession(ticket);
                    translationSessions.put(sessionId, session);
                    agent.activeSessionIds().add(sessionId);
                    emitter.complete(new TranslationTicketResult(true, acceptance.getReason(), session));
                } else {
                    emitter.complete(new TranslationTicketResult(false, acceptance.getReason(), null));
                }
            });

            agent.emitResponse(ListenResponse.newBuilder()
                    .setTranslationProposal(TranslationProposal.newBuilder()
                            .setSessionId(sessionId)
                            .setTargetType(ticket.targetFormat()))
                    .build());
        });
    }

    public void cleanupAgent(String agentId) {
        AgentImpl agent = agents.remove(agentId);
        if (agent == null) return;

        log.info("Cleaning up agent {} ({}). Failing {} active sessions.",
                agentId, agent.displayName(), agent.activeSessionIds().size());

        for (String sessionId : agent.activeSessionIds()) {
            if (sessionId.startsWith("JOB-")) {
                DefaultJobSession session = jobSessions.remove(sessionId);
                if (session != null) {
                    session.updateStatus(JobStatus.newBuilder()
                            .setState(JobState.JOB_STATE_FAILED)
                            .setMessage("Agent disconnected unexpectedly")
                            .build());
                }
            } else if (sessionId.startsWith("TRN-")) {
                DefaultTranslationSession session = translationSessions.remove(sessionId);
                if (session != null) {
                    session.updateStatus(TranslationStatus.newBuilder()
                            .setState(TranslationState.TRANSLATION_STATE_FAILED)
                            .setMessage("Agent disconnected unexpectedly")
                            .build());
                }
            }
        }
    }

    public record AgentImpl(String displayName, List<Capability> capabilities,
                            BroadcastProcessor<ListenResponse> processor,
                            Set<String> activeSessionIds) implements Agent {
        public AgentImpl(String displayName, List<Capability> capabilities) {
            this(displayName, capabilities, BroadcastProcessor.create(), ConcurrentHashMap.newKeySet());
        }

        @Override
        public boolean isReady() {
            return true; // We can always accept items, they just drop if no one listens
        }

        public Multi<ListenResponse> listenStream() {
            return processor;
        }

        public void emitResponse(ListenResponse response) {
            processor.onNext(response);
        }
    }
}

