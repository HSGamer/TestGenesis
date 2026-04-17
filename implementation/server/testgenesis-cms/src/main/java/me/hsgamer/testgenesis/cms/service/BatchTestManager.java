package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.BatchStatus;
import me.hsgamer.testgenesis.cms.core.TestBatchSession;
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
        TestBatchSession batch = new TestBatchSession(testId, test.getName(), parallel, totalSessions);
        addBatchSession(batch);
        batch.setStatus(BatchStatus.RUNNING);

        if (parallel) {
            executeParallel(batch, agentIds, iterations, extraPayloadIds);
        } else {
            executeSequential(batch, agentIds, iterations, extraPayloadIds, 0, 0);
        }

        return batch.getBatchId();
    }

    private void executeParallel(TestBatchSession batch, List<String> agentIds, int iterations, List<Long> extraPayloadIds) {
        for (String agentId : agentIds) {
            for (int i = 0; i < iterations; i++) {
                testSessionManager.startTest(batch.getTestId(), agentId, extraPayloadIds).subscribe().with(
                    res -> {
                        if (res.accepted()) {
                            batch.markSessionAccepted(res.session(), null);
                        } else {
                            batch.markSessionFailed();
                        }
                    },
                    err -> batch.markSessionFailed()
                );
            }
        }
    }

    private void executeSequential(TestBatchSession batch, List<String> agentIds, int iterations, List<Long> extraPayloadIds, int agentIndex, int iterIndex) {
        if (batch.getStatus() == BatchStatus.CANCELLED) return;

        if (agentIndex >= agentIds.size()) {
            return;
        }

        if (iterIndex >= iterations) {
            executeSequential(batch, agentIds, iterations, extraPayloadIds, agentIndex + 1, 0);
            return;
        }

        String agentId = agentIds.get(agentIndex);
        testSessionManager.startTest(batch.getTestId(), agentId, extraPayloadIds).subscribe().with(
            res -> {
                if (res.accepted()) {
                    batch.markSessionAccepted(res.session(), () ->
                        executeSequential(batch, agentIds, iterations, extraPayloadIds, agentIndex, iterIndex + 1)
                    );
                } else {
                    batch.markSessionFailed();
                }
            },
            err -> batch.markSessionFailed()
        );
    }
}
