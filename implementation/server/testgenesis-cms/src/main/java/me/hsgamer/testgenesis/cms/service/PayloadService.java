package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
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
}
