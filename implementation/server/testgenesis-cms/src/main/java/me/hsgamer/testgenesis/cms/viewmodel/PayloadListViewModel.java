package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record PayloadListViewModel(
        List<PayloadView> payloads
) {
    public record PayloadView(Long id, String type, int attachmentCount) {
    }
}
