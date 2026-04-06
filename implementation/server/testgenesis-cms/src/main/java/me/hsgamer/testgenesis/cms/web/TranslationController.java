package me.hsgamer.testgenesis.cms.web;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jstach.jstachio.JStachio;
import me.hsgamer.testgenesis.cms.db.entity.TestEntity;
import me.hsgamer.testgenesis.cms.db.entity.TranslationEntity;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import me.hsgamer.testgenesis.cms.db.repository.TranslationRepository;
import me.hsgamer.testgenesis.cms.service.TranslationService;

import java.util.List;
import java.util.Optional;

/**
 * Controller for translation-related operations.
 */
public class TranslationController implements HttpService {

    private final TranslationService translationService;
    private final TranslationRepository translationRepo;
    private final TestRepository testRepo;
    private final JStachio jstachio = JStachio.of();

    public TranslationController(TranslationService translationService, 
                                 TranslationRepository translationRepo,
                                 TestRepository testRepo) {
        this.translationService = translationService;
        this.translationRepo = translationRepo;
        this.testRepo = testRepo;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/translations", this::listTranslations);
        rules.get("/translations/{id}", this::translationDetail);
        rules.get("/translate", this::startTranslation);
    }

    private void listTranslations(ServerRequest req, ServerResponse res) {
        List<TranslationListView.TranslationView> views = translationRepo.findAll()
                .map(entity -> {
                    String testName = testRepo.findById(entity.getSourceTestId())
                            .map(TestEntity::getName)
                            .orElse("Deleted Test (" + entity.getSourceTestId() + ")");
                    
                    String agentName = entity.getResult() != null ? entity.getResult().getAgentDisplayName() : "N/A";
                    String status = entity.getResult() != null ? entity.getResult().getStatus() : "PENDING";
                    String requestedAt = entity.getRequestedAt() != null ? entity.getRequestedAt().toString() : "N/A";

                    return new TranslationListView.TranslationView(
                            entity.getId(),
                            testName,
                            entity.getTargetFormat(),
                            agentName,
                            status,
                            requestedAt
                    );
                })
                .toList();

        res.headers().contentType(MediaTypes.TEXT_HTML);
        res.send(jstachio.execute(new TranslationListView(views)));
    }

    private void translationDetail(ServerRequest req, ServerResponse res) {
        Long id = Long.valueOf(req.path().pathParameters().get("id"));
        Optional<TranslationEntity> entityOpt = translationRepo.findById(id);

        if (entityOpt.isEmpty()) {
            res.status(Status.NOT_FOUND_404).send("Translation not found");
            return;
        }

        TranslationEntity entity = entityOpt.get();
        String testName = testRepo.findById(entity.getSourceTestId())
                .map(TestEntity::getName)
                .orElse("Deleted Test (" + entity.getSourceTestId() + ")");

        TranslationDetailView view = new TranslationDetailView(
                entity.getId(),
                testName,
                entity.getTargetFormat(),
                entity.getResult() != null ? entity.getResult().getAgentDisplayName() : "N/A",
                entity.getResult() != null ? entity.getResult().getStatus() : "PENDING",
                entity.getRequestedAt() != null ? entity.getRequestedAt().toString() : "N/A",
                entity.getResult() != null && entity.getResult().getCompletedAt() != null ? entity.getResult().getCompletedAt().toString() : "N/A",
                entity.getResult() != null ? entity.getResult().getTelemetryLog() : "",
                entity.getResult() != null && entity.getResult().getResultPayload() != null ? entity.getResult().getResultPayload().getId() : null
        );

        res.headers().contentType(MediaTypes.TEXT_HTML);
        res.send(jstachio.execute(view));
    }

    private void startTranslation(ServerRequest req, ServerResponse res) {
        String agentId = req.query().get("agentId");
        String testIdStr = req.query().get("testId");
        String targetFormat = req.query().get("targetFormat");

        if (agentId == null || testIdStr == null || targetFormat == null) {
            res.status(Status.BAD_REQUEST_400).send("Missing agentId, testId, or targetFormat");
            return;
        }

        Long testId = Long.valueOf(testIdStr);
        translationService.startTranslation(agentId, testId, targetFormat).handle((ok, ex) -> {
            if (ex != null) {
                res.status(Status.INTERNAL_SERVER_ERROR_500).send("Error: " + ex.getMessage());
            } else if (Boolean.TRUE.equals(ok)) {
                res.status(Status.SEE_OTHER_303).header("Location", "/translations").send();
            } else {
                res.status(Status.ACCEPTED_202).send("Translation requested but not yet accepted by agent.");
            }
            return null;
        });
    }
}
