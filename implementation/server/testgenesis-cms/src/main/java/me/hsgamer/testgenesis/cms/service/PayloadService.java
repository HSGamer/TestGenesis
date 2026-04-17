package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.uap.v1.Payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.protobuf.Value.newBuilder;
import static me.hsgamer.testgenesis.cms.util.ProtoUtil.*;

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
        metadataBuilder.putFields("_originSessionId", newBuilder().setStringValue(sessionId).build());
        if (proto.hasAttachment()) {
            metadataBuilder.putFields("_originName", newBuilder().setStringValue(proto.getAttachment().getName()).build());
        }
        entity.setMetadata(structToJson(metadataBuilder.build()));

        entity.persist();
        return entity;
    }

    public Optional<PayloadEntity> findByOrigin(String sessionId, String name) {
        return PayloadEntity.<PayloadEntity>find("metadata LIKE ?1 AND metadata LIKE ?2",
                "%" + sessionId + "%", "%" + name + "%")
            .stream()
            .filter(e -> {
                java.util.Map<String, Object> map = structToMap(jsonToStruct(e.getMetadata()));
                return sessionId.equals(map.get("_originSessionId")) && name.equals(map.get("_originName"));
            })
            .findFirst();
    }

    @Transactional
    public List<Payload> preparePayloads(List<Long> payloadIds) {
        List<Payload> protos = new ArrayList<>();
        if (payloadIds == null) return protos;

        for (Long id : payloadIds) {
            PayloadEntity.findByIdOptional(id)
                .map(p -> (PayloadEntity) p)
                .map(PayloadEntity::toProto)
                .ifPresent(protos::add);
        }
        return protos;
    }
}
