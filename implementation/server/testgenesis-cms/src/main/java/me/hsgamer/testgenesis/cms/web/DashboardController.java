package me.hsgamer.testgenesis.cms.web;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jstach.jstachio.JStachio;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import me.hsgamer.testgenesis.cms.db.repository.TestResultRepository;
import me.hsgamer.testgenesis.cms.service.UAPService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for the main dashboard. Manually wired.
 */
public class DashboardController implements HttpService {

    private final TestResultRepository resultRepo;
    private final TestRepository testRepo;
    private final JStachio jstachio = JStachio.of();

    public DashboardController(TestResultRepository resultRepo, TestRepository testRepo) {
        this.resultRepo = resultRepo;
        this.testRepo = testRepo;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::dashboard);
    }

    private void dashboard(ServerRequest req, ServerResponse res) {
        List<DashboardView.AgentView> agentViews = UAPService.INSTANCE.getAgents().entrySet().stream()
                .map(entry -> {
                    boolean canTranslate = entry.getValue().capabilities().stream()
                            .anyMatch(cap -> cap.getFormatCase() == me.hsgamer.testgenesis.uap.v1.Capability.FormatCase.TRANSLATION);
                    
                    List<String> targetFormats = entry.getValue().capabilities().stream()
                            .filter(cap -> cap.getFormatCase() == me.hsgamer.testgenesis.uap.v1.Capability.FormatCase.TRANSLATION)
                            .flatMap(cap -> cap.getTranslation().getTargetPayloadsList().stream())
                            .map(me.hsgamer.testgenesis.uap.v1.PayloadRequirement::getType)
                            .distinct()
                            .toList();

                    return new DashboardView.AgentView(
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

        List<DashboardView.ResultView> resultViews = resultRepo.findAll()
                .limit(10)
                .map(entity -> new DashboardView.ResultView(
                        entity.getId(),
                        entity.getTest() != null ? entity.getTest().getName() : "Unknown",
                        entity.getAgentDisplayName(),
                        entity.getStatus(),
                        entity.getRegisteredAt() != null ? entity.getRegisteredAt().toString() : "N/A"
                ))
                .toList();

        List<DashboardView.TestView> tests = testRepo.findAll()
                .map(entity -> new DashboardView.TestView(entity.getId(), entity.getName()))
                .toList();

        res.headers().contentType(MediaTypes.TEXT_HTML);
        res.send(jstachio.execute(new DashboardView(agentViews, resultViews, tests)));
    }
}
