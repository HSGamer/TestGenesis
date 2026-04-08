package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record DashboardViewModel(
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
    ) {
    }

    public record ResultView(
            Long id,
            String testName,
            String agentName,
            String status,
            String registeredAt
    ) {
    }

    public record TestView(
            Long id,
            String name
    ) {
    }
}
