package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.TestTicket;
import me.hsgamer.testgenesis.cms.core.TestTicketResult;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.uap.v1.Payload;

import java.util.List;
import me.hsgamer.testgenesis.cms.core.TestInfo;
import me.hsgamer.testgenesis.cms.core.TestTicket;
import me.hsgamer.testgenesis.uap.v1.Payload;
import me.hsgamer.testgenesis.cms.core.TestTicketResult;
import me.hsgamer.testgenesis.cms.core.BatchStatus;
import me.hsgamer.testgenesis.cms.core.TestBatchSession;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.uap.v1.TestStatus;
import me.hsgamer.testgenesis.uap.v1.TestState;
import me.hsgamer.testgenesis.cms.util.StatusUtil;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.context.ManagedExecutor;

@ApplicationScoped
public class TestManager {

    @Inject
    UAPService uapService;

    @Inject
    TestService testService;

    @Inject
    PayloadService payloadService;

    @Inject
    ManagedExecutor managedExecutor;

    public Uni<TestTicketResult> startTest(Long testId, String agentId, List<Long> extraPayloadIds) {
        return Uni.createFrom().item(() -> {
            TestInfo info = testService.getTestInfo(testId);
            java.util.Set<Long> allIds = new java.util.HashSet<>(info.payloadIds());
            if (extraPayloadIds != null) allIds.addAll(extraPayloadIds);
            
            List<Payload> protos = payloadService.preparePayloads(allIds.stream().toList());
            return new TestTicket(info.testType(), protos);
        })
        .runSubscriptionOn(managedExecutor)
        .onItem().transformToUni(ticket -> uapService.registerTest(agentId, ticket));
    }

    public String startBatchTest(Long testId, String agentId, List<Long> extraPayloadIds, int iterations, boolean parallel) {
        TestEntity test = testService.findById(testId)
            .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        
        TestBatchSession batch = new TestBatchSession(testId, test.getName(), parallel, iterations);
        uapService.addBatchSession(batch);
        batch.setStatus(BatchStatus.RUNNING);

        if (parallel) {
            for (int i = 0; i < iterations; i++) {
                startTest(testId, agentId, extraPayloadIds).subscribe().with(res -> {
                    if (res.accepted()) {
                        TestSession session = res.session();
                        batch.addSession(session);
                        session.onCompletion(() -> checkBatchCompletion(batch));
                    } else if (batch.getStatus() != BatchStatus.CANCELLED) {
                        batch.setStatus(BatchStatus.FAILED);
                    }
                }, err -> {
                    if (batch.getStatus() != BatchStatus.CANCELLED) {
                        batch.setStatus(BatchStatus.FAILED);
                    }
                });
            }
        } else {
            startNextIteration(batch, testId, agentId, extraPayloadIds, 0);
        }
        
        return batch.getBatchId();
    }

    private void startNextIteration(TestBatchSession batch, Long testId, String agentId, List<Long> extraPayloadIds, int index) {
        if (batch.getStatus() == BatchStatus.CANCELLED) return;
        if (index >= batch.getTotalIterations()) {
            checkBatchCompletion(batch);
            return;
        }
        
        startTest(testId, agentId, extraPayloadIds).subscribe().with(res -> {
            if (res.accepted()) {
                TestSession session = res.session();
                batch.addSession(session);
                session.onCompletion(() -> startNextIteration(batch, testId, agentId, extraPayloadIds, index + 1));
            } else if (batch.getStatus() != BatchStatus.CANCELLED) {
                batch.setStatus(BatchStatus.FAILED);
            }
        }, err -> {
            if (batch.getStatus() != BatchStatus.CANCELLED) {
                batch.setStatus(BatchStatus.FAILED);
            }
        });
    }

    private void checkBatchCompletion(TestBatchSession batch) {
        if (batch.getCompletedCount() >= batch.getTotalIterations()) {
            batch.setStatus(BatchStatus.COMPLETED);
        }
    }
}
