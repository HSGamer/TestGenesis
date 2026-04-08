package me.hsgamer.testgenesis.cms.controller;

import me.hsgamer.testgenesis.cms.entity.TestProject;
import me.hsgamer.testgenesis.cms.entity.Translation;
import me.hsgamer.testgenesis.cms.repository.TestProjectRepository;
import me.hsgamer.testgenesis.cms.repository.TranslationRepository;
import me.hsgamer.testgenesis.cms.service.TranslationService;
import me.hsgamer.testgenesis.cms.viewmodel.TranslationDetailViewModel;
import me.hsgamer.testgenesis.cms.viewmodel.TranslationListViewModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class TranslationController {

    private final TranslationService translationService;
    private final TranslationRepository translationRepo;
    private final TestProjectRepository testRepo;

    public TranslationController(TranslationService translationService,
                                 TranslationRepository translationRepo,
                                 TestProjectRepository testRepo) {
        this.translationService = translationService;
        this.translationRepo = translationRepo;
        this.testRepo = testRepo;
    }

    @GetMapping("/translations")
    public String listTranslations(Model model) {
        List<TranslationListViewModel.TranslationView> views = translationRepo.findAll().stream()
                .map(entity -> {
                    String testName = testRepo.findById(entity.getSourceTestId())
                            .map(TestProject::getName)
                            .orElse("Deleted Test (" + entity.getSourceTestId() + ")");

                    String agentName = entity.getResult() != null ? entity.getResult().getAgentDisplayName() : "N/A";
                    String status = entity.getResult() != null ? entity.getResult().getStatus() : "PENDING";
                    String requestedAt = entity.getRequestedAt() != null ? entity.getRequestedAt().toString() : "N/A";

                    return new TranslationListViewModel.TranslationView(
                            entity.getId(),
                            testName,
                            entity.getTargetFormat(),
                            agentName,
                            status,
                            requestedAt
                    );
                })
                .toList();

        model.addAttribute("translations", views);
        return "translations";
    }

    @GetMapping("/translations/{id}")
    public String translationDetail(@PathVariable(name = "id") Long id, Model model) {
        Optional<Translation> entityOpt = translationRepo.findById(id);

        if (entityOpt.isEmpty()) {
            return "error/404";
        }

        Translation entity = entityOpt.get();
        String testName = testRepo.findById(entity.getSourceTestId())
                .map(TestProject::getName)
                .orElse("Deleted Test (" + entity.getSourceTestId() + ")");

        TranslationDetailViewModel view = new TranslationDetailViewModel(
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

        model.addAttribute("translation", view);
        return "translation_detail";
    }

    @GetMapping("/translate")
    public Object startTranslation(@RequestParam(name = "agentId") String agentId, @RequestParam(name = "testId") Long testId, @RequestParam(name = "targetFormat") String targetFormat) {
        return translationService.startTranslation(agentId, testId, targetFormat).handle((ok, ex) -> {
            if (ex != null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + ex.getMessage());
            } else if (Boolean.TRUE.equals(ok)) {
                return "redirect:/translations";
            } else {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Translation requested but not yet accepted by agent.");
            }
        }).join();
    }
}
