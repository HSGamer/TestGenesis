package me.hsgamer.testgenesis.cms.service;

import io.grpc.Context;
import io.grpc.Metadata;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.*;
import me.hsgamer.testgenesis.cms.dto.AgentGuidedInfo;
import me.hsgamer.testgenesis.cms.dto.AgentTranslationInfo;
import me.hsgamer.testgenesis.cms.dto.TestTypeInfo;
import me.hsgamer.testgenesis.cms.dto.TranslationTypeInfo;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@ApplicationScoped
@Slf4j
public class UAPService {
    public static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    public static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    public static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Consumer<SessionAcceptance>> pendingSessionAcceptances = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, TestSession> testSessions = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, TranslationSession> translationSessions = new ConcurrentHashMap<>();


    public Map<String, Agent> getAgents() {
        return Collections.unmodifiableMap(agents);
    }

    public Map<String, AgentImpl> getInternalAgents() {
        return agents;
    }

    public Uni<TestTicketResult> registerTest(String agentId, TestTicket ticket) {
        return registerSession(agentId, "JOB-",
                proposal -> proposal.setTest(TestProposalDetails.newBuilder().setType(ticket.testType()).build()),
                (sessionId, acceptance) -> {
                    if (acceptance.getAccepted()) {
                        TestSession session = new TestSession(sessionId, ticket);
                        testSessions.put(sessionId, session);
                        return new TestTicketResult(true, acceptance.getReason(), session);
                    } else {
                        return new TestTicketResult(false, acceptance.getReason(), null);
                    }
                });
    }

    public Uni<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        return registerSession(agentId, "TRN-",
                proposal -> proposal.setTranslation(TranslationProposalDetails.newBuilder().setType(ticket.targetFormat()).build()),
                (sessionId, acceptance) -> {
                    if (acceptance.getAccepted()) {
                        TranslationSession session = new TranslationSession(sessionId, ticket);
                        translationSessions.put(sessionId, session);
                        return new TranslationTicketResult(true, acceptance.getReason(), session);
                    } else {
                        return new TranslationTicketResult(false, acceptance.getReason(), null);
                    }
                });
    }

    private <R extends TicketResult> Uni<R> registerSession(
            String agentId,
            String sessionIdPrefix,
            Consumer<SessionProposal.Builder> proposalDecorator,
            BiFunction<String, SessionAcceptance, R> resultProducer
    ) {
        AgentImpl agent = agents.get(agentId);
        if (agent == null) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not found: " + agentId));
        }
        if (!agent.isReady()) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not ready: " + agentId));
        }

        String sessionId = sessionIdPrefix + UUID.randomUUID();

        return Uni.createFrom().emitter(emitter -> {
            pendingSessionAcceptances.put(sessionId, acceptance -> {
                pendingSessionAcceptances.remove(sessionId);

                R result = resultProducer.apply(sessionId, acceptance);

                if (acceptance.getAccepted()) {
                    agent.activeSessionIds().add(sessionId);

                    if (result.session() != null) {
                        result.session().onCompletion(() -> agent.activeSessionIds().remove(sessionId));
                    }
                }

                emitter.complete(result);

                if (acceptance.getAccepted()) {
                    agent.emitResponse(ListenResponse.newBuilder()
                            .setSessionReady(SessionReady.newBuilder().setSessionId(sessionId).build())
                            .build());
                }
            });

            SessionProposal.Builder proposalBuilder = SessionProposal.newBuilder().setSessionId(sessionId);
            proposalDecorator.accept(proposalBuilder);

            agent.emitResponse(ListenResponse.newBuilder()
                    .setSessionProposal(proposalBuilder.build())
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
                TestSession session = testSessions.remove(sessionId);
                if (session != null) {
                    session.updateStatus(TestStatus.newBuilder()
                            .setState(TestState.TEST_STATE_FAILED)
                            .setMessage("Agent disconnected unexpectedly")
                            .build());
                }
            } else if (sessionId.startsWith("TRN-")) {
                TranslationSession session = translationSessions.remove(sessionId);
                if (session != null) {
                    session.updateStatus(TranslationStatus.newBuilder()
                            .setState(TranslationState.TRANSLATION_STATE_FAILED)
                            .setMessage("Agent disconnected unexpectedly")
                            .build());
                }
            }
        }

    }

    public Set<String> getAvailablePayloadTypes() {
        Set<String> types = new TreeSet<>();
        for (AgentImpl agent : agents.values()) {
            for (Capability capability : agent.capabilities()) {
                switch (capability.getFormatCase()) {
                    case TEST -> {
                        TestCapability test = capability.getTest();
                        for (PayloadRequirement req : test.getPayloadsList()) {
                            types.add(req.getType());
                        }
                    }
                    case TRANSLATION -> {
                        TranslationCapability trans = capability.getTranslation();
                        for (PayloadRequirement req : trans.getSourcePayloadsList()) {
                            types.add(req.getType());
                        }
                        for (PayloadRequirement req : trans.getTargetPayloadsList()) {
                            types.add(req.getType());
                        }
                    }
                    case FORMAT_NOT_SET -> {
                    }
                }
            }
        }
        return types;
    }

    public List<AgentGuidedInfo> getAgentGuidedInfos() {
        List<AgentGuidedInfo> infos = new ArrayList<>();
        for (Map.Entry<String, AgentImpl> entry : agents.entrySet()) {
            String id = entry.getKey();
            AgentImpl agent = entry.getValue();
            List<TestTypeInfo> testTypes = new ArrayList<>();

            for (Capability capability : agent.capabilities()) {
                if (capability.getFormatCase() == Capability.FormatCase.TEST) {
                    TestCapability test = capability.getTest();
                    List<String> required = test.getPayloadsList().stream()
                            .filter(PayloadRequirement::getIsRequired)
                            .map(PayloadRequirement::getType)
                            .toList();
                    List<String> optional = test.getPayloadsList().stream()
                            .filter(p -> !p.getIsRequired())
                            .map(PayloadRequirement::getType)
                            .toList();
                    testTypes.add(new TestTypeInfo(test.getType(), required, optional));
                }
            }
            infos.add(new AgentGuidedInfo(id, agent.displayName(), testTypes));
        }
        return infos;
    }

    public Map<String, Set<String>> getPayloadMimeTypeMapping() {
        return agents.values().stream()
                .flatMap(agent -> agent.capabilities().stream())
                .flatMap(cap -> switch (cap.getFormatCase()) {
                    case TEST -> cap.getTest().getPayloadsList().stream();
                    case TRANSLATION -> Stream.concat(
                            cap.getTranslation().getSourcePayloadsList().stream(),
                            cap.getTranslation().getTargetPayloadsList().stream()
                    );
                    default -> Stream.empty();
                })
                .collect(Collectors.groupingBy(
                        PayloadRequirement::getType,
                        Collectors.flatMapping(
                                req -> req.getAcceptedMimeTypesList().stream(),
                                Collectors.toSet()
                        )
                ));
    }

    public List<AgentTranslationInfo> getAgentTranslationInfos() {
        List<AgentTranslationInfo> infos = new ArrayList<>();
        for (Map.Entry<String, AgentImpl> entry : agents.entrySet()) {
            String id = entry.getKey();
            AgentImpl agent = entry.getValue();
            List<TranslationTypeInfo> translations = new ArrayList<>();

            for (Capability capability : agent.capabilities()) {
                if (capability.getFormatCase() == Capability.FormatCase.TRANSLATION) {
                    TranslationCapability trans = capability.getTranslation();
                    List<String> sourceTypes = trans.getSourcePayloadsList().stream()
                            .map(PayloadRequirement::getType)
                            .toList();
                    List<String> targetTypes = trans.getTargetPayloadsList().stream()
                            .map(PayloadRequirement::getType)
                            .toList();
                    translations.add(new TranslationTypeInfo(trans.getType(), sourceTypes, targetTypes));
                }
            }
            if (!translations.isEmpty()) {
                infos.add(new AgentTranslationInfo(id, agent.displayName(), translations));
            }
        }
        return infos;
    }


    public static class AgentImpl implements Agent {
        private final String displayName;
        private final List<Capability> capabilities;
        private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
        private Consumer<ListenResponse> streamDispatcher;

        public AgentImpl(String displayName, List<Capability> capabilities) {
            this.displayName = displayName;
            this.capabilities = capabilities;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public List<Capability> capabilities() {
            return capabilities;
        }

        public Set<String> activeSessionIds() {
            return activeSessionIds;
        }

        @Override
        public boolean isReady() {
            return streamDispatcher != null;
        }

        public void setStreamDispatcher(Consumer<ListenResponse> dispatcher) {
            this.streamDispatcher = dispatcher;
        }

        public void emitResponse(ListenResponse response) {
            if (this.streamDispatcher != null) {
                this.streamDispatcher.accept(response);
            }
        }
    }
}


