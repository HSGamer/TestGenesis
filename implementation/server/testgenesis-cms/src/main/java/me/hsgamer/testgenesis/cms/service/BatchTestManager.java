package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.BatchStatus;
import me.hsgamer.testgenesis.cms.core.TestBatchSession;
import me.hsgamer.testgenesis.cms.core.TestTicket;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BatchTestManager {
    private final Map<String, TestBatchSession> batchSessions = new ConcurrentHashMap<>();

    @Inject
    TestService testService;

    @Inject
    TestSessionManager testSessionManager;

    public Optional<TestBatchSession> getBatchSession(String id) {
        return Optional.ofNullable(batchSessions.get(id));
    }

    public Collection<TestBatchSession> getBatchSessions() {
        return Collections.unmodifiableCollection(batchSessions.values());
    }

    public void addBatchSession(TestBatchSession session) {
        batchSessions.put(session.getBatchId(), session);
    }

    public String startBatchTest(Long testId, List<String> agentIds, List<Long> extraPayloadIds, int iterations, boolean parallel) {
        TestEntity test = testService.findById(testId)
            .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        int totalSessions = agentIds.size() * iterations;
        TestTicket ticket = testSessionManager.prepareTicket(testId, extraPayloadIds);
        TestBatchSession batch = new TestBatchSession(test.getName(), ticket, totalSessions);
        addBatchSession(batch);

        for (int i = 0; i < iterations; i++) {
            agentIds.forEach(batch::addAgent);
        }

        batch.setStatus(BatchStatus.RUNNING);

        if (parallel) {
            String agentId;
            while ((agentId = batch.pollAgent()) != null) {
                register(batch, agentId);
            }
        } else {
            batch.addListener(() -> {
                if (batch.getStatus() == BatchStatus.RUNNING) {
                    processNextSequential(batch);
                }
            });
            processNextSequential(batch);
        }

        return batch.getBatchId();
    }

    private synchronized void processNextSequential(TestBatchSession batch) {
        if (batch.getStatus() != BatchStatus.RUNNING) return;
        if (batch.getRunningCount() > 0) return;

        String agentId = batch.pollAgent();
        if (agentId != null) {
            register(batch, agentId);
        }
    }

    private void register(TestBatchSession batch, String agentId) {
        testSessionManager.startTest(agentId, batch.getTicket()).subscribe().with(
            res -> {
                if (res.accepted()) {
                    batch.addSession(res.session());
                } else {
                    batch.markRegistrationFailed();
                }
            },
            err -> batch.markRegistrationFailed()
        );
    }
}
