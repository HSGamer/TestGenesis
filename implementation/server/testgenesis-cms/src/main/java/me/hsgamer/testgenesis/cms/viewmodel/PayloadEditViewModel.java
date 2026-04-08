package me.hsgamer.testgenesis.cms.viewmodel;

import java.util.List;

public record PayloadEditViewModel(
        PayloadView payload,
        List<AttachmentView> attachments,
        boolean isEdit
) {
    public record PayloadView(Long id, String type, String metadataJson) {
    }

    public record AttachmentView(Long id, String name, String contentType, boolean isText, String textContent) {
    }
}
