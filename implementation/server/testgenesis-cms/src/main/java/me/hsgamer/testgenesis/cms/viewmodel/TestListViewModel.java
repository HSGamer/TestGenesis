package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record TestListViewModel(
        List<TestView> tests
) {
    public record TestView(Long id, String name, String testType, int payloadCount) {
    }
}
