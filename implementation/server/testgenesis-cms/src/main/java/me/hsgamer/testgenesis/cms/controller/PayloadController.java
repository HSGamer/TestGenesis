package me.hsgamer.testgenesis.cms.controller;

import me.hsgamer.testgenesis.cms.entity.Attachment;
import me.hsgamer.testgenesis.cms.entity.Payload;
import me.hsgamer.testgenesis.cms.repository.AttachmentRepository;
import me.hsgamer.testgenesis.cms.repository.PayloadRepository;
import me.hsgamer.testgenesis.cms.viewmodel.PayloadEditViewModel;
import me.hsgamer.testgenesis.cms.viewmodel.PayloadListViewModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class PayloadController {

    private final PayloadRepository payloadRepo;
    private final AttachmentRepository attachmentRepo;

    public PayloadController(PayloadRepository payloadRepo, AttachmentRepository attachmentRepo) {
        this.payloadRepo = payloadRepo;
        this.attachmentRepo = attachmentRepo;
    }

    @GetMapping("/payloads")
    public String listPayloads(Model model) {
        List<PayloadListViewModel.PayloadView> views = payloadRepo.findAll().stream()
                .map(p -> new PayloadListViewModel.PayloadView(p.getId(), p.getPayloadType(), p.getAttachments().size()))
                .toList();
        model.addAttribute("payloads", views);
        return "payloads";
    }

    @GetMapping("/payloads/new")
    public String newPayload(Model model) {
        model.addAttribute("payload", new PayloadEditViewModel.PayloadView(null, "", "{}"));
        model.addAttribute("isEdit", false);
        return "payload_edit";
    }

    @GetMapping("/payloads/edit/{id}")
    public String editPayload(@PathVariable Long id, Model model) {
        Payload payload = payloadRepo.findById(id).orElseThrow();

        List<PayloadEditViewModel.AttachmentView> attachments = payload.getAttachments().stream()
                .map(this::toAttachmentView)
                .toList();

        PayloadEditViewModel.PayloadView payloadView = new PayloadEditViewModel.PayloadView(
                payload.getId(),
                payload.getPayloadType(),
                payload.getMetadataJson()
        );

        model.addAttribute("payload", payloadView);
        model.addAttribute("attachments", attachments);
        model.addAttribute("isEdit", true);
        return "payload_edit";
    }

    private PayloadEditViewModel.AttachmentView toAttachmentView(Attachment entity) {
        boolean isText = isTextFile(entity.getName());
        String textContent = isText ? new String(entity.getContent(), StandardCharsets.UTF_8) : null;
        return new PayloadEditViewModel.AttachmentView(
                entity.getId(),
                entity.getName(),
                entity.getContentType(),
                isText,
                textContent
        );
    }

    private boolean isTextFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".file") || lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".xml");
    }

    @PostMapping("/payloads/save")
    public String savePayload(@RequestParam String type, @RequestParam String metadataJson) {
        Payload payload = new Payload();
        payload.setPayloadType(type);
        payload.setMetadataJson(metadataJson);
        payloadRepo.save(payload);
        return "redirect:/payloads";
    }

    @PostMapping("/payloads/update/{id}")
    public String updatePayload(@PathVariable Long id, @RequestParam String type, @RequestParam String metadataJson) {
        Payload payload = payloadRepo.findById(id).orElseThrow();
        payload.setPayloadType(type);
        payload.setMetadataJson(metadataJson);
        payloadRepo.save(payload);
        return "redirect:/payloads/edit/" + id;
    }

    @PostMapping("/payloads/delete/{id}")
    public String deletePayload(@PathVariable Long id) {
        payloadRepo.deleteById(id);
        return "redirect:/payloads";
    }

    @GetMapping("/attachments/download/{id}")
    @ResponseBody
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Attachment attachment = attachmentRepo.findById(id).orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getName() + "\"")
                .body(new ByteArrayResource(attachment.getContent()));
    }

    @PostMapping("/attachments/update-text/{id}")
    public String updateAttachmentText(@PathVariable Long id, @RequestParam String textContent) {
        Attachment attachment = attachmentRepo.findById(id).orElseThrow();
        attachment.setContent(textContent.getBytes(StandardCharsets.UTF_8));
        attachmentRepo.save(attachment);
        return "redirect:/payloads/edit/" + attachment.getPayload().getId();
    }

    @PostMapping("/attachments/delete/{id}")
    public String deleteAttachment(@PathVariable Long id) {
        Attachment attachment = attachmentRepo.findById(id).orElseThrow();
        Long payloadId = attachment.getPayload().getId();
        attachmentRepo.deleteById(id);
        return "redirect:/payloads/edit/" + payloadId;
    }

    @PostMapping("/attachments/upload/{payloadId}")
    public String uploadAttachment(@PathVariable Long payloadId, @RequestParam("file") MultipartFile file) throws IOException {
        Payload payload = payloadRepo.findById(payloadId).orElseThrow();
        if (!file.isEmpty()) {
            Attachment attachment = new Attachment();
            attachment.setName(file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setContent(file.getBytes());
            attachment.setPayload(payload);
            attachmentRepo.save(attachment);
        }
        return "redirect:/payloads/edit/" + payloadId;
    }
}
