package me.hsgamer.testgenesis.cms.web;

import io.jstach.jstache.JStache;
import java.util.List;

/**
 * JStachio view for the main dashboard.
 */
@JStache(path = "me/hsgamer/testgenesis/cms/web/dashboard.mustache")
public record DashboardView(
        List<AgentView> agents,
        List<ResultView> recentResults,
        List<TestView> tests
) {
    public record AgentView(
            String id,
            String displayName,
            String capabilities,
            boolean canTranslate,
            List<String> targetFormats
    ) {}

    public record ResultView(
            Long id,
            String testName,
            String agentName,
            String status,
            String registeredAt
    ) {}

    public record TestView(
            Long id,
            String name
    ) {}
}
