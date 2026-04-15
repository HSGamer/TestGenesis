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
    public PayloadEntity savePayload(String sessionId, Payload proto, String name, String description) {
        PayloadEntity entity = new PayloadEntity();
        entity.fillFromProto(proto);
        entity.setName(name);
        entity.setDescription(description);

        // Add origin metadata
        com.google.protobuf.Struct.Builder metadataBuilder = proto.getMetadata().toBuilder();
        metadataBuilder.putFields("_originSessionId", com.google.protobuf.Value.newBuilder().setStringValue(sessionId).build());
        if (proto.hasAttachment()) {
            metadataBuilder.putFields("_originName", com.google.protobuf.Value.newBuilder().setStringValue(proto.getAttachment().getName()).build());
        }
        entity.setMetadata(me.hsgamer.testgenesis.cms.util.ProtoUtil.structToJson(metadataBuilder.build()));

        entity.persist();
        return entity;
    }

    public Optional<PayloadEntity> findByOrigin(String sessionId, String name) {
        return PayloadEntity.<PayloadEntity>find("metadata LIKE ?1 AND metadata LIKE ?2",
                "%" + sessionId + "%", "%" + name + "%")
            .stream()
            .filter(e -> {
                java.util.Map<String, Object> map = me.hsgamer.testgenesis.cms.util.ProtoUtil.structToMap(me.hsgamer.testgenesis.cms.util.ProtoUtil.jsonToStruct(e.getMetadata()));
                return sessionId.equals(map.get("_originSessionId")) && name.equals(map.get("_originName"));
            })
            .findFirst();
    }
}
