package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.uap.v1.Payload;
import me.hsgamer.testgenesis.uap.v1.TranslationResult;

import java.util.ArrayList;
import java.util.List;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class PayloadService {


    public List<PayloadEntity> listAll() {
        return PayloadEntity.listAll();
    }

    public Optional<PayloadEntity> findById(Long id) {
        return PayloadEntity.findByIdOptional(id);
    }

    @Transactional
    public PayloadEntity create(PayloadEntity entity) {
        entity.persist();
        return entity;
    }

    @Transactional
    public PayloadEntity update(Long id, PayloadEntity updated) {
        PayloadEntity entity = PayloadEntity.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Payload not found: " + id);
        }
        entity.setName(updated.getName());
        entity.setDescription(updated.getDescription());
        entity.setType(updated.getType());
        entity.setMetadata(updated.getMetadata());

        if (updated.getAttachmentData() != null) {
            entity.setAttachmentName(updated.getAttachmentName());
            entity.setAttachmentMimeType(updated.getAttachmentMimeType());
            entity.setAttachmentData(updated.getAttachmentData());
        }

        return entity;
    }

    @Transactional
    public void delete(Long id) {
        PayloadEntity.deleteById(id);
    }

    @Transactional
    public List<TranslationSession.GeneratedPayload> saveTranslatedPayloads(TranslationResult result) {
        List<TranslationSession.GeneratedPayload> generated = new ArrayList<>();
        for (Payload p : result.getPayloadsList()) {
            try {
                PayloadEntity entity = new PayloadEntity();
                entity.fillFromProto(p);
                entity.persist();
                generated.add(new TranslationSession.GeneratedPayload(entity.id));
                log.info("Saved translated payload: {}", entity.getName());
            } catch (Exception e) {
                log.error("Failed to save translated payload", e);
            }
        }
        return generated;
    }
}
