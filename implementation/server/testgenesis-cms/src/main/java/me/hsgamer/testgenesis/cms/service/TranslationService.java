package me.hsgamer.testgenesis.cms.service;

import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.cms.db.entity.PayloadEntity;
import me.hsgamer.testgenesis.cms.db.entity.TestEntity;
import me.hsgamer.testgenesis.cms.db.entity.TranslationEntity;
import me.hsgamer.testgenesis.cms.db.entity.TranslationResultEntity;
import me.hsgamer.testgenesis.cms.db.repository.PayloadRepository;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import me.hsgamer.testgenesis.cms.db.repository.TranslationRepository;
import me.hsgamer.testgenesis.cms.db.repository.TranslationResultRepository;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationState;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Orchestrates the translation process.
 * Decoupled from TestEntity as per requirements.
 */
public class TranslationService {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final TranslationRepository translationRepo;
    private final TranslationResultRepository resultRepo;
    private final TestRepository testRepo;
    private final PayloadRepository payloadRepo;

    public TranslationService(TranslationRepository translationRepo, TranslationResultRepository resultRepo,
                              TestRepository testRepo, PayloadRepository payloadRepo) {
        this.translationRepo = translationRepo;
        this.resultRepo = resultRepo;
        this.testRepo = testRepo;
        this.payloadRepo = payloadRepo;
    }

    public CompletableFuture<Boolean> startTranslation(String agentId, Long testId, String targetFormat) {
        Optional<TestEntity> testOpt = testRepo.findById(testId);
        if (testOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Test not found: " + testId));
        }

        TestEntity test = testOpt.get();

        TranslationTicket ticket = new TranslationTicket(
                targetFormat,
                test.getPayloads().stream()
                        .map(p -> me.hsgamer.testgenesis.uap.v1.Payload.newBuilder()
                                .setType(p.getPayloadType())
                                .build())
                        .toList()
        );

        return UAPService.INSTANCE.registerTranslation(agentId, ticket).thenApply(result -> {
            if (result.accepted()) {
                TranslationSession session = result.session();

                TranslationEntity translation = new TranslationEntity();
                translation.setSourceTestId(testId);
                translation.setTargetFormat(targetFormat);
                translation = translationRepo.save(translation);

                TranslationResultEntity resultEntity = new TranslationResultEntity();
                resultEntity.setTranslation(translation);
                resultEntity.setAgentDisplayName(UAPService.INSTANCE.getAgents().get(agentId).displayName());
                resultEntity.setStatus(TranslationState.TRANSLATION_STATE_PROCESSING.name());
                resultEntity = resultRepo.save(resultEntity);

                final Long resultId = resultEntity.getId();

                // Status updates
                session.addStatusConsumer(status -> updateResultStatus(resultId, status));
                
                // Result arrival
                session.addResultConsumer(translationResult -> handleFinalResult(resultId, translationResult));

                return true;
            }
            return false;
        });
    }

    private void updateResultStatus(Long resultId, TranslationStatus status) {
        resultRepo.findById(resultId).ifPresent(entity -> {
            entity.setStatus(status.getState().name());
            resultRepo.save(entity);
            logger.info("TranslationResult %d status updated: %s".formatted(resultId, status.getState()));
        });
    }

    public void handleFinalResult(Long resultId, TranslationResult translationResult) {
        resultRepo.findById(resultId).ifPresent(entity -> {
            entity.setStatus(translationResult.getStatus().getState().name());
            entity.setCompletedAt(Instant.now());
            
            // Save final telemetry if available
            entity.setTelemetryLog(translationResult.getTranslationLog());

            // If COMPLETED, create the standalone PayloadEntity
            if (translationResult.getStatus().getState() == TranslationState.TRANSLATION_STATE_COMPLETED) {
                for (me.hsgamer.testgenesis.uap.v1.Payload p : translationResult.getPayloadsList()) {
                    PayloadEntity payload = new PayloadEntity();
                    payload.setPayloadType(p.getType());
                    // Use metadata as JSON if possible, otherwise empty
                    payload.setMetadataJson(String.valueOf(p.getMetadata()));
                    
                    // Attach content (Attachments)
                    for (me.hsgamer.testgenesis.uap.v1.Attachment a : p.getContentList()) {
                        me.hsgamer.testgenesis.cms.db.entity.AttachmentEntity attachmentEntity = new me.hsgamer.testgenesis.cms.db.entity.AttachmentEntity();
                        attachmentEntity.setName(a.getName());
                        attachmentEntity.setContentType(a.getMimeType());
                        attachmentEntity.setContent(a.getData().toByteArray());
                        attachmentEntity.setPayload(payload);
                        payload.getAttachments().add(attachmentEntity);
                    }
                    
                    payload = payloadRepo.save(payload);
                    
                    // Link the result to the generated payload
                    entity.setResultPayload(payload);
                }
            }

            resultRepo.save(entity);
            logger.info("Completed TranslationResult %d and saved standalone PayloadEntity".formatted(resultId));
        });
    }
}
