package me.hsgamer.testgenesis.cms.controller;

import me.hsgamer.testgenesis.cms.repository.TestProjectRepository;
import me.hsgamer.testgenesis.cms.repository.TestResultRepository;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.cms.viewmodel.DashboardViewModel;
import me.hsgamer.testgenesis.uap.v1.Capability;
import me.hsgamer.testgenesis.uap.v1.PayloadRequirement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final TestProjectRepository testRepo;
    private final TestResultRepository resultRepo;
    private final UAPService uapService;

    public DashboardController(TestProjectRepository testRepo, TestResultRepository resultRepo, UAPService uapService) {
        this.testRepo = testRepo;
        this.resultRepo = resultRepo;
        this.uapService = uapService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<DashboardViewModel.AgentView> agentViews = uapService.getAgents().entrySet().stream()
                .map(entry -> {
                    boolean canTranslate = entry.getValue().capabilities().stream()
                            .anyMatch(cap -> cap.getFormatCase() == Capability.FormatCase.TRANSLATION);

                    List<String> targetFormats = entry.getValue().capabilities().stream()
                            .filter(cap -> cap.getFormatCase() == Capability.FormatCase.TRANSLATION)
                            .flatMap(cap -> cap.getTranslation().getTargetPayloadsList().stream())
                            .map(PayloadRequirement::getType)
                            .distinct()
                            .toList();

                    return new DashboardViewModel.AgentView(
                            entry.getKey(),
                            entry.getValue().displayName(),
                            entry.getValue().capabilities().stream()
                                    .map(cap -> cap.hasTest() ? cap.getTest().getType() : (cap.hasTranslation() ? "translator" : "unknown"))
                                    .collect(Collectors.joining(", ")),
                            canTranslate,
                            targetFormats
                    );
                })
                .toList();

        List<DashboardViewModel.ResultView> resultViews = resultRepo.findAll(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "registeredAt")))
                .stream()
                .map(entity -> new DashboardViewModel.ResultView(
                        entity.getId(),
                        entity.getTest() != null ? entity.getTest().getName() : "Unknown",
                        entity.getAgentDisplayName(),
                        entity.getStatus(),
                        entity.getRegisteredAt() != null ? entity.getRegisteredAt().toString() : "N/A"
                ))
                .toList();

        List<DashboardViewModel.TestView> tests = testRepo.findAll().stream()
                .map(entity -> new DashboardViewModel.TestView(entity.getId(), entity.getName()))
                .toList();

        model.addAttribute("agents", agentViews);
        model.addAttribute("recentResults", resultViews);
        model.addAttribute("tests", tests);

        return "dashboard";
    }
}
