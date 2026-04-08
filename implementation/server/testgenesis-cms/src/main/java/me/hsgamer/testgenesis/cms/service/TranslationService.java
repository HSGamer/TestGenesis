package me.hsgamer.testgenesis.cms.service;

import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.core.TranslationTicket;
import me.hsgamer.testgenesis.cms.entity.Attachment;
import me.hsgamer.testgenesis.cms.entity.Payload;
import me.hsgamer.testgenesis.cms.entity.TestProject;
import me.hsgamer.testgenesis.cms.entity.Translation;
import me.hsgamer.testgenesis.cms.repository.PayloadRepository;
import me.hsgamer.testgenesis.cms.repository.TestProjectRepository;
import me.hsgamer.testgenesis.cms.repository.TranslationRepository;
import me.hsgamer.testgenesis.cms.repository.TranslationResultRepository;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;
import me.hsgamer.testgenesis.uap.v1.TranslationState;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Service
public class TranslationService {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final TranslationRepository translationRepo;
    private final TranslationResultRepository resultRepo;
    private final TestProjectRepository testRepo;
    private final PayloadRepository payloadRepo;
    private final UAPService uapService;

    public TranslationService(TranslationRepository translationRepo, TranslationResultRepository resultRepo,
                              TestProjectRepository testRepo, PayloadRepository payloadRepo, UAPService uapService) {
        this.translationRepo = translationRepo;
        this.resultRepo = resultRepo;
        this.testRepo = testRepo;
        this.payloadRepo = payloadRepo;
        this.uapService = uapService;
    }

    public CompletableFuture<Boolean> startTranslation(String agentId, Long testId, String targetFormat) {
        Optional<TestProject> testOpt = testRepo.findById(testId);
        if (testOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Test not found: " + testId));
        }

        TestProject test = testOpt.get();

        TranslationTicket ticket = new TranslationTicket(
                targetFormat,
                test.getPayloads().stream()
                        .map(p -> me.hsgamer.testgenesis.uap.v1.Payload.newBuilder()
                                .setType(p.getPayloadType())
                                .build())
                        .toList()
        );

        return uapService.registerTranslation(agentId, ticket).thenApply(result -> {
            if (result.accepted()) {
                TranslationSession session = result.session();

                Translation translation = new Translation();
                translation.setSourceTestId(testId);
                translation.setTargetFormat(targetFormat);
                translation = translationRepo.save(translation);

                me.hsgamer.testgenesis.cms.entity.TranslationResult resultEntity = new me.hsgamer.testgenesis.cms.entity.TranslationResult();
                resultEntity.setTranslation(translation);
                resultEntity.setAgentDisplayName(uapService.getAgents().get(agentId).displayName());
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

            // If COMPLETED, create the standalone Payload
            if (translationResult.getStatus().getState() == TranslationState.TRANSLATION_STATE_COMPLETED) {
                for (me.hsgamer.testgenesis.uap.v1.Payload p : translationResult.getPayloadsList()) {
                    Payload payload = new Payload();
                    payload.setPayloadType(p.getType());
                    payload.setMetadataJson(String.valueOf(p.getMetadata()));

                    // Attach content (Attachments)
                    for (me.hsgamer.testgenesis.uap.v1.Attachment a : p.getContentList()) {
                        Attachment attachmentEntity = new Attachment();
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
            logger.info("Completed TranslationResult %d and saved standalone Payload".formatted(resultId));
        });
    }
}
