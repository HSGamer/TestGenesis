package me.hsgamer.testgenesis.cms.service;

import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.Session;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.uap.v1.*;

@Singleton
@Default
@GrpcService
@Slf4j
public class UAPService extends MutinyAgentHubGrpc.AgentHubImplBase {
    private static final Context.Key<String> CLIENT_ID_CTX = Context.key("clientId");
    private static final Context.Key<String> SESSION_ID_CTX = Context.key("sessionId");
    private static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);

    @Inject
    AgentManager agentManager;

    @Inject
    TestSessionManager testSessionManager;

    @Inject
    TranslationManager translationManager;

    @Override
    public Uni<AgentRegistrationResponse> register(AgentRegistration req) {
        String id = agentManager.registerAgent(req.getDisplayName(), req.getCapabilitiesList());
        return Uni.createFrom().item(AgentRegistrationResponse.newBuilder().setClientId(id).build());
    }

    @Override
    public Multi<ListenResponse> listen(Multi<ListenRequest> reqs) {
        String id = CLIENT_ID_CTX.get();
        if (id == null || agentManager.getAgent(id).isEmpty())
            return Multi.createFrom().failure(Status.NOT_FOUND.withDescription("Invalid Client ID").asRuntimeException());

        reqs.subscribe().with(
            v -> {
                if (v.hasSessionAcceptance()) {
                    String sid = v.getSessionAcceptance().getSessionId();
                    if (sid.startsWith("JOB-")) {
                        testSessionManager.handleAcceptance(sid, v.getSessionAcceptance());
                    } else if (sid.startsWith("TRN-")) {
                        translationManager.handleAcceptance(sid, v.getSessionAcceptance());
                    }
                }
            },
            e -> cleanupAgent(id),
            () -> cleanupAgent(id)
        );
        return Multi.createFrom().emitter(e -> agentManager.setAgentDispatcher(id, e::emit));
    }

    @Override
    public Multi<TestInit> execute(Multi<TestResponse> reqs) {
        TestSession s = testSessionManager.getTestSession(SESSION_ID_CTX.get()).orElse(null);
        return handleStream(s, reqs, (sess, emitter) -> {
            emitter.emit(TestInit.newBuilder().setTestType(s.getTicket().testType()).addAllPayloads(s.getTicket().payloads()).build());
        });
    }

    @Override
    public Multi<TranslationInit> translate(Multi<TranslationResponse> reqs) {
        TranslationSession s = translationManager.getTranslationSession(SESSION_ID_CTX.get()).orElse(null);
        return handleStream(s, reqs, (sess, emitter) -> {
            emitter.emit(TranslationInit.newBuilder().setTargetFormat(s.getTicket().targetFormat()).addAllPayloads(s.getTicket().payloads()).build());
        });
    }

    private <R, InitT> Multi<InitT> handleStream(Session<R> session, Multi<R> reqs, java.util.function.BiConsumer<Session<R>, io.smallrye.mutiny.subscription.MultiEmitter<? super InitT>> initer) {
        if (session == null) return Multi.createFrom().failure(Status.NOT_FOUND.asRuntimeException());
        reqs.subscribe().with(
            session::handleResponse,
            e -> session.fail("Stream error: " + e.getMessage()),
            () -> log.debug("Stream closed for session {}", session.getSessionId())
        );
        return Multi.createFrom().emitter(e -> initer.accept(session, e));
    }

    private void cleanupAgent(String id) {
        agentManager.getAgent(id).ifPresent(agent -> {
            if (agent instanceof AgentManager.AgentImpl agentImpl) {
                agentImpl.activeSessions.forEach(sid -> {
                    if (sid.startsWith("JOB-")) {
                        testSessionManager.failSession(sid, "Agent disconnected");
                    } else if (sid.startsWith("TRN-")) {
                        translationManager.failSession(sid, "Agent disconnected");
                    }
                });
            }
            agentManager.removeAgent(id);
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
}
