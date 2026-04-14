package me.hsgamer.testgenesis.cms.service;

import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
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

@Singleton
@Default
@GrpcService
@Slf4j
public class UAPService extends MutinyAgentHubGrpc.AgentHubImplBase {
    private static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    private static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    private static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();
    private final Map<String, Consumer<SessionAcceptance>> pendingAcceptances = new ConcurrentHashMap<>();
    private final Map<String, TestSession> testSessions = new ConcurrentHashMap<>();
    private final Map<String, TranslationSession> translationSessions = new ConcurrentHashMap<>();

    public Collection<Agent> getAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public Optional<TestSession> getTestSession(String id) {
        return Optional.ofNullable(testSessions.get(id));
    }

    public Collection<TestSession> getTestSessions() {
        return Collections.unmodifiableCollection(testSessions.values());
    }

    public Optional<TranslationSession> getTranslationSession(String id) {
        return Optional.ofNullable(translationSessions.get(id));
    }

    public Collection<TranslationSession> getTranslationSessions() {
        return Collections.unmodifiableCollection(translationSessions.values());
    }

    public Uni<TestTicketResult> registerTest(String agentId, TestTicket ticket) {
        return registerSession(agentId, "JOB-",
            p -> p.setTest(TestProposalDetails.newBuilder().setType(ticket.testType()).build()),
            (id, ok) -> {
                if (!ok) return new TestTicketResult(false, "Rejected by agent", null);
                TestSession s = new TestSession(id, ticket);
                testSessions.put(id, s);
                return new TestTicketResult(true, "Accepted", s);
            });
    }

    public Uni<TranslationTicketResult> registerTranslation(String agentId, TranslationTicket ticket) {
        return registerSession(agentId, "TRN-",
            p -> p.setTranslation(TranslationProposalDetails.newBuilder().setType(ticket.targetFormat()).build()),
            (id, ok) -> {
                if (!ok) return new TranslationTicketResult(false, "Rejected by agent", null);
                TranslationSession s = new TranslationSession(id, ticket);
                translationSessions.put(id, s);
                return new TranslationTicketResult(true, "Accepted", s);
            });
    }

    public List<AgentGuidedInfo> getAgentGuidedInfos() {
        return agents.values().stream().filter(Agent::isReady).map(a -> {
            List<TestTypeInfo> tests = a.capabilities.stream()
                .filter(Capability::hasTest).map(c -> {
                    var t = c.getTest();
                    var req = t.getPayloadsList().stream().filter(PayloadRequirement::getIsRequired).map(PayloadRequirement::getType).toList();
                    var opt = t.getPayloadsList().stream().filter(p -> !p.getIsRequired()).map(PayloadRequirement::getType).toList();
                    return new TestTypeInfo(t.getType(), req, opt);
                }).toList();
            return new AgentGuidedInfo(a.id, a.displayName, tests);
        }).toList();
    }

    public List<AgentTranslationInfo> getAgentTranslationInfos() {
        return agents.values().stream().filter(Agent::isReady).map(a -> {
            List<TranslationTypeInfo> trans = a.capabilities.stream()
                .filter(Capability::hasTranslation).map(c -> {
                    var t = c.getTranslation();
                    var src = t.getSourcePayloadsList().stream().map(PayloadRequirement::getType).toList();
                    var trg = t.getTargetPayloadsList().stream().map(PayloadRequirement::getType).toList();
                    return new TranslationTypeInfo(t.getType(), src, trg);
                }).toList();
            return new AgentTranslationInfo(a.id, a.displayName, trans);
        }).filter(i -> !i.supportedTranslations().isEmpty()).toList();
    }

    public Set<String> getAvailablePayloadTypes() {
        return agents.values().stream().filter(Agent::isReady).flatMap(a -> a.capabilities.stream())
            .flatMap(c -> switch (c.getFormatCase()) {
                case TEST -> c.getTest().getPayloadsList().stream().map(PayloadRequirement::getType);
                case TRANSLATION ->
                    Stream.concat(c.getTranslation().getSourcePayloadsList().stream(), c.getTranslation().getTargetPayloadsList().stream()).map(PayloadRequirement::getType);
                default -> Stream.empty();
            }).collect(Collectors.toCollection(TreeSet::new));
    }

    public Map<String, Set<String>> getPayloadMimeTypeMapping() {
        return agents.values().stream().filter(Agent::isReady).flatMap(a -> a.capabilities.stream())
            .flatMap(c -> switch (c.getFormatCase()) {
                case TEST -> c.getTest().getPayloadsList().stream();
                case TRANSLATION ->
                    Stream.concat(c.getTranslation().getSourcePayloadsList().stream(), c.getTranslation().getTargetPayloadsList().stream());
                default -> Stream.empty();
            }).collect(Collectors.groupingBy(PayloadRequirement::getType, Collectors.flatMapping(r -> r.getAcceptedMimeTypesList().stream(), Collectors.toSet())));
    }

    @Override
    public Uni<AgentRegistrationResponse> register(AgentRegistration req) {
        String id = UUID.randomUUID().toString();
        agents.put(id, new AgentImpl(id, req.getDisplayName(), req.getCapabilitiesList()));
        log.info("Agent registered: {} ({})", id, req.getDisplayName());
        return Uni.createFrom().item(AgentRegistrationResponse.newBuilder().setClientId(id).build());
    }

    @Override
    public Multi<ListenResponse> listen(Multi<ListenRequest> reqs) {
        String id = CLIENT_ID_CTX.get();
        AgentImpl agent = id != null ? agents.get(id) : null;
        if (agent == null)
            return Multi.createFrom().failure(Status.NOT_FOUND.withDescription("Invalid Client ID").asRuntimeException());

        reqs.subscribe().with(
            v -> {
                if (v.hasSessionAcceptance())
                    Optional.ofNullable(pendingAcceptances.get(v.getSessionAcceptance().getSessionId())).ifPresent(c -> c.accept(v.getSessionAcceptance()));
            },
            e -> cleanupAgent(id),
            () -> cleanupAgent(id)
        );
        return Multi.createFrom().emitter(e -> agent.setDispatcher(e::emit));
    }

    @Override
    public Multi<TestInit> execute(Multi<TestResponse> reqs) {
        TestSession s = testSessions.get(SESSION_ID_CTX.get());
        return handleStream(s, reqs, (sess, emitter) -> {
            emitter.emit(TestInit.newBuilder().setTestType(s.getTicket().testType()).addAllPayloads(s.getTicket().payloads()).build());
        });
    }

    @Override
    public Multi<TranslationInit> translate(Multi<TranslationResponse> reqs) {
        TranslationSession s = translationSessions.get(SESSION_ID_CTX.get());
        return handleStream(s, reqs, (sess, emitter) -> {
            emitter.emit(TranslationInit.newBuilder().setTargetFormat(s.getTicket().targetFormat()).addAllPayloads(s.getTicket().payloads()).build());
        });
    }

    private <R, InitT> Multi<InitT> handleStream(me.hsgamer.testgenesis.cms.core.Session<R> session, Multi<R> reqs, java.util.function.BiConsumer<me.hsgamer.testgenesis.cms.core.Session<R>, io.smallrye.mutiny.subscription.MultiEmitter<? super InitT>> initer) {
        if (session == null) return Multi.createFrom().failure(Status.NOT_FOUND.asRuntimeException());
        reqs.subscribe().with(
            session::handleResponse,
            e -> session.fail("Stream error: " + e.getMessage()),
            () -> log.debug("Stream closed for session {}", session.getSessionId())
        );
        return Multi.createFrom().emitter(e -> initer.accept(session, e));
    }

    private <R extends TicketResult> Uni<R> registerSession(String aid, String prefix, Consumer<SessionProposal.Builder> dec, BiFunction<String, Boolean, R> prod) {
        AgentImpl agent = agents.get(aid);
        if (agent == null || !agent.isReady())
            return Uni.createFrom().failure(new IllegalStateException("Agent not available"));
        String sid = prefix + UUID.randomUUID();
        return Uni.createFrom().emitter(e -> {
            pendingAcceptances.put(sid, acc -> {
                pendingAcceptances.remove(sid);
                R res = prod.apply(sid, acc.getAccepted());
                if (acc.getAccepted()) {
                    agent.activeSessions.add(sid);
                    if (res.session() != null) res.session().onCompletion(() -> agent.activeSessions.remove(sid));
                    agent.send(ListenResponse.newBuilder().setSessionReady(SessionReady.newBuilder().setSessionId(sid).build()).build());
                }
                e.complete(res);
            });
            var prop = SessionProposal.newBuilder().setSessionId(sid);
            dec.accept(prop);
            agent.send(ListenResponse.newBuilder().setSessionProposal(prop.build()).build());
        });
    }

    private void cleanupAgent(String id) {
        AgentImpl a = agents.remove(id);
        if (a == null) return;
        a.activeSessions.forEach(sid -> {
            if (sid.startsWith("JOB-")) {
                Optional.ofNullable(testSessions.remove(sid)).ifPresent(s -> s.updateStatus(TestStatus.newBuilder()
                    .setState(TestState.TEST_STATE_FAILED)
                    .setMessage("Agent disconnected")
                    .build()));
            } else if (sid.startsWith("TRN-")) {
                Optional.ofNullable(translationSessions.remove(sid)).ifPresent(s -> s.updateStatus(TranslationStatus.newBuilder()
                    .setState(TranslationState.TRANSLATION_STATE_FAILED)
                    .setMessage("Agent disconnected")
                    .build()));
            }
        });
    }

    @GlobalInterceptor
    @Singleton
    public static class IdInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String cid = headers.get(CLIENT_ID_KEY), sid = headers.get(SESSION_ID_KEY);
            Context ctx = Context.current();
            if (cid != null) ctx = ctx.withValue(CLIENT_ID_CTX, cid);
            if (sid != null) ctx = ctx.withValue(SESSION_ID_CTX, sid);
            return Contexts.interceptCall(ctx, call, headers, next);
        }
    }

    private static class AgentImpl implements Agent {
        final String id, displayName;
        final List<Capability> capabilities;
        final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
        private Consumer<ListenResponse> dispatcher;

        AgentImpl(String id, String name, List<Capability> caps) {
            this.id = id;
            this.displayName = name;
            this.capabilities = caps;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public List<Capability> capabilities() {
            return capabilities;
        }

        @Override
        public boolean isReady() {
            return dispatcher != null;
        }

        @Override
        public boolean supportsTestType(String type) {
            return capabilities.stream().anyMatch(c -> c.hasTest() && c.getTest().getType().equals(type));
        }

        void setDispatcher(Consumer<ListenResponse> d) {
            this.dispatcher = d;
        }

        void send(ListenResponse r) {
            if (dispatcher != null) dispatcher.accept(r);
        }
    }
}
