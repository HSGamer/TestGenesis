package me.hsgamer.testgenesis.cms.service;

import me.hsgamer.testgenesis.cms.core.JobSession;
import me.hsgamer.testgenesis.cms.core.JobTicket;
import me.hsgamer.testgenesis.cms.db.entity.TestEntity;
import me.hsgamer.testgenesis.cms.db.entity.TestResultEntity;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import me.hsgamer.testgenesis.cms.db.repository.TestResultRepository;
import me.hsgamer.testgenesis.uap.v1.JobResult;
import me.hsgamer.testgenesis.uap.v1.JobState;
import me.hsgamer.testgenesis.uap.v1.JobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Orchestrates test execution using manual wiring. No Service Registry is used here.
 */
public class TestExecutionService {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final TestRepository testRepo;
    private final TestResultRepository resultRepo;
    
    // In-memory buffer for telemetry, only saved at the end
    private final Map<Long, StringBuilder> sessionTelemetry = new ConcurrentHashMap<>();

    public TestExecutionService(TestRepository testRepo, TestResultRepository resultRepo) {
        this.testRepo = testRepo;
        this.resultRepo = resultRepo;
    }

    public CompletableFuture<Boolean> startTest(String agentId, Long testId) {
        Optional<TestEntity> testOpt = testRepo.findById(testId);
        if (testOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Test not found: " + testId));
        }

        TestEntity test = testOpt.get();
        
        JobTicket ticket = new JobTicket(
                test.getTestType(),
                test.getPayloads().stream()
                        .map(p -> me.hsgamer.testgenesis.uap.v1.Payload.newBuilder()
                                .setType(p.getPayloadType())
                                .build())
                        .toList()
        );

        return UAPService.INSTANCE.registerJob(agentId, ticket).thenApply(result -> {
            if (result.accepted()) {
                JobSession session = result.session();
                
                TestResultEntity resultEntity = new TestResultEntity();
                resultEntity.setTest(test);
                resultEntity.setAgentDisplayName(UAPService.INSTANCE.getAgents().get(agentId).displayName());
                resultEntity.setStatus(JobState.JOB_STATE_RUNNING.name());
                resultEntity = resultRepo.save(resultEntity);
                
                final Long resultId = resultEntity.getId();
                sessionTelemetry.put(resultId, new StringBuilder());

                // Buffer telemetry in-memory per user request
                session.addTelemetryConsumer(telemetry -> {
                    StringBuilder sb = sessionTelemetry.get(resultId);
                    if (sb != null) {
                        sb.append("[").append(Instant.now()).append("] ")
                          .append(telemetry.getMessage()).append("\n");
                    }
                });

                // Status updates (no DB flush for telemetry here)
                session.addStatusConsumer(status -> updateResultStatus(resultId, status));
                
                // Final result persistence
                // Note: Completion logic should call handleFinalResult
                
                return true;
            }
            return false;
        });
    }

    private void updateResultStatus(Long resultId, JobStatus status) {
        resultRepo.findById(resultId).ifPresent(entity -> {
            entity.setStatus(status.getState().name());
            resultRepo.save(entity);
            
            // If terminal state, finalize
            if (isTerminal(status.getState())) {
                // In a real implementation, we'd wait for the final JobResult object
                // but for now we'll trigger finalization on completion status
                logger.info("TestResult %d entered terminal state: %s".formatted(resultId, status.getState()));
            }
        });
    }

    private boolean isTerminal(JobState state) {
        return state == JobState.JOB_STATE_COMPLETED || state == JobState.JOB_STATE_FAILED;
    }

    public void handleFinalResult(Long resultId, JobResult jobResult) {
        resultRepo.findById(resultId).ifPresent(entity -> {
            entity.setStatus(jobResult.getStatus().getState().name());
            if (jobResult.hasSummary()) {
                var summary = jobResult.getSummary();
                if (summary.hasStartTime()) {
                    entity.setStartTime(Instant.ofEpochSecond(summary.getStartTime().getSeconds()));
                }
                if (summary.hasTotalDuration()) {
                    entity.setTotalDuration(Duration.ofSeconds(summary.getTotalDuration().getSeconds()));
                }
            }
            
            // Save the buffered telemetry log at the end
            StringBuilder sb = sessionTelemetry.remove(resultId);
            if (sb != null) {
                entity.setTelemetryLog(sb.toString());
            }

            entity.setCompletedAt(Instant.now());
            resultRepo.save(entity);
            logger.info("Completed and persisted TestResult %d with final telemetry".formatted(resultId));
        });
    }
}
