package me.hsgamer.testgenesis.cms.service;

import io.grpc.Context;
import io.grpc.Metadata;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.*;
import me.hsgamer.testgenesis.cms.core.impl.DefaultJobSession;
import me.hsgamer.testgenesis.cms.core.impl.DefaultTranslationSession;
import me.hsgamer.testgenesis.uap.v1.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
public class UAPService {
    public static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    public static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    public static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JobAcceptance>> pendingJobAcceptances = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TranslationAcceptance>> pendingTranslationAcceptances = new ConcurrentHashMap<>();
    private final Map<String, DefaultJobSession> jobSessions = new ConcurrentHashMap<>();
    private final Map<String, DefaultTranslationSession> translationSessions = new ConcurrentHashMap<>();

    public Map<String, Agent> getAgents() {
        return Collections.unmodifiableMap(agents);
    }

    public Map<String, AgentImpl> getInternalAgents() {
        return agents;
    }

    public Map<String, Consumer<JobAcceptance>> getPendingJobAcceptances() {
        return pendingJobAcceptances;
    }

    public Map<String, Consumer<TranslationAcceptance>> getPendingTranslationAcceptances() {
        return pendingTranslationAcceptances;
    }

    public Map<String, DefaultJobSession> getJobSessions() {
        return jobSessions;
    }

    public Map<String, DefaultTranslationSession> getTranslationSessions() {
        return translationSessions;
    }

    public CompletableFuture<JobTicketResult> registerJob(String agentId, JobTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "JOB-" + UUID.randomUUID();
        CompletableFuture<JobTicketResult> future = new CompletableFuture<>();

        pendingJobAcceptances.put(sessionId, acceptance -> {
            pendingJobAcceptances.remove(sessionId);
            if (acceptance.getAccepted()) {
                DefaultJobSession session = new DefaultJobSession(ticket);
                jobSessions.put(sessionId, session);
                future.complete(new JobTicketResult(true, acceptance.getReason(), session));
            } else {
                future.complete(new JobTicketResult(false, acceptance.getReason(), null));
            }
        });

        agent.listener().onNext(ListenResponse.newBuilder()
                .setJobProposal(JobProposal.newBuilder()
                        .setSessionId(sessionId)
                        .setTestType(ticket.testType()))
                .build());

        return future;
    }

    public CompletableFuture<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = "TRN-" + UUID.randomUUID();
        CompletableFuture<TranslationTicketResult> future = new CompletableFuture<>();

        pendingTranslationAcceptances.put(sessionId, acceptance -> {
            pendingTranslationAcceptances.remove(sessionId);
            if (acceptance.getAccepted()) {
                DefaultTranslationSession session = new DefaultTranslationSession(ticket);
                translationSessions.put(sessionId, session);
                future.complete(new TranslationTicketResult(true, acceptance.getReason(), session));
            } else {
                future.complete(new TranslationTicketResult(false, acceptance.getReason(), null));
            }
        });

        agent.listener().onNext(ListenResponse.newBuilder()
                .setTranslationProposal(TranslationProposal.newBuilder()
                        .setSessionId(sessionId)
                        .setTargetType(ticket.targetFormat()))
                .build());

        return future;
    }

    public record AgentImpl(String displayName, List<Capability> capabilities,
                            AtomicReference<io.grpc.stub.StreamObserver<ListenResponse>> listenerRef) implements Agent {
        @Override
        public boolean isReady() {
            return listenerRef.get() != null;
        }

        public io.grpc.stub.StreamObserver<ListenResponse> listener() {
            return listenerRef.get();
        }
    }
}
