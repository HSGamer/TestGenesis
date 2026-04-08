package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record TestEditViewModel(
        TestView test,
        List<PayloadOption> availablePayloads,
        boolean isEdit
) {
    public record TestView(Long id, String name, String testType) {
    }

    public record PayloadOption(Long id, String name, String type, boolean selected) {
    }
}
