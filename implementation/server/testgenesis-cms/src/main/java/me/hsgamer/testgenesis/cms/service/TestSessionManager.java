package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.*;
import me.hsgamer.testgenesis.uap.v1.*;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
public class TestSessionManager {
    private final Map<String, TestSession> testSessions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<SessionAcceptance>> pendingAcceptances = new ConcurrentHashMap<>();

    @Inject
    AgentManager agentManager;

    @Inject
    TestService testService;

    @Inject
    PayloadService payloadService;

    @Inject
    ManagedExecutor managedExecutor;

    public Optional<TestSession> getTestSession(String id) {
        return Optional.ofNullable(testSessions.get(id));
    }

    public Collection<TestSession> getTestSessions() {
        return Collections.unmodifiableCollection(testSessions.values());
    }

    public Uni<TestTicketResult> registerTest(String agentId, TestTicket ticket) {
        Optional<Agent> agentOpt = agentManager.getAgent(agentId);
        if (agentOpt.isEmpty() || !agentOpt.get().isReady()) {
            return Uni.createFrom().failure(new IllegalStateException("Agent not available"));
        }
        Agent agent = agentOpt.get();
        String agentName = agent.displayName();
        String sid = "JOB-" + UUID.randomUUID();

        return Uni.createFrom().emitter(e -> {
            pendingAcceptances.put(sid, acc -> {
                pendingAcceptances.remove(sid);
                if (!acc.getAccepted()) {
                    e.complete(new TestTicketResult(false, "Rejected by agent", null));
                    return;
                }

                TestSession s = new TestSession(sid, ticket, agentId, agentName);
                testSessions.put(sid, s);

                if (agent instanceof AgentManager.AgentImpl agentImpl) {
                    agentImpl.activeSessions.add(sid);
                    s.onCompletion(() -> agentImpl.activeSessions.remove(sid));
                }

                agentManager.sendToAgent(agentId, ListenResponse.newBuilder()
                    .setSessionReady(SessionReady.newBuilder().setSessionId(sid).build())
                    .build());

                e.complete(new TestTicketResult(true, "Accepted", s));
            });

            SessionProposal prop = SessionProposal.newBuilder()
                .setSessionId(sid)
                .setTest(TestProposalDetails.newBuilder().setType(ticket.testType()).build())
                .build();

            agentManager.sendToAgent(agentId, ListenResponse.newBuilder()
                .setSessionProposal(prop).build());
        });
    }

    public void handleAcceptance(String sessionId, SessionAcceptance acceptance) {
        Optional.ofNullable(pendingAcceptances.get(sessionId)).ifPresent(c -> c.accept(acceptance));
    }

    public TestTicket prepareTicket(Long testId, List<Long> extraPayloadIds) {
        TestInfo info = testService.getTestInfo(testId);
        Set<Long> allIds = new HashSet<>(info.payloadIds());
        if (extraPayloadIds != null) allIds.addAll(extraPayloadIds);

        List<Payload> protos = payloadService.preparePayloads(allIds.stream().toList());
        return new TestTicket(info.testType(), protos);
    }

    public Uni<TestTicketResult> startTest(Long testId, String agentId, List<Long> extraPayloadIds) {
        return Uni.createFrom().item(() -> prepareTicket(testId, extraPayloadIds))
            .runSubscriptionOn(managedExecutor)
            .onItem().transformToUni(ticket -> registerTest(agentId, ticket));
    }

    public Uni<TestTicketResult> startTest(String agentId, TestTicket ticket) {
        return registerTest(agentId, ticket);
    }

    public void failSession(String sessionId, String reason) {
        Optional.ofNullable(testSessions.get(sessionId)).ifPresent(s -> s.fail(reason));
    }
}
