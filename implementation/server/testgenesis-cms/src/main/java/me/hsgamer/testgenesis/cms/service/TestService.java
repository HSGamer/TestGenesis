package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.hsgamer.testgenesis.cms.core.TestInfo;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class TestService {
    public List<TestEntity> listAll() {
        return TestEntity.listAll();
    }

    public Optional<TestEntity> findById(Long id) {
        return TestEntity.findByIdOptional(id);
    }

    @Transactional
    public TestEntity create(TestEntity entity, List<Long> payloadIds) {
        if (payloadIds != null && !payloadIds.isEmpty()) {
            entity.setPayloads(fetchPayloads(payloadIds));
        }
        entity.persist();
        return entity;
    }

    @Transactional
    public TestEntity update(Long id, TestEntity updated, List<Long> payloadIds) {
        TestEntity entity = TestEntity.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Test not found: " + id);
        }
        entity.setName(updated.getName());
        entity.setDescription(updated.getDescription());
        entity.setTestType(updated.getTestType());

        if (payloadIds != null) {
            entity.setPayloads(fetchPayloads(payloadIds));
        } else {
            entity.getPayloads().clear();
        }

        return entity;
    }

    @Transactional
    public TestEntity copy(Long id) {
        TestEntity original = TestEntity.findById(id);
        if (original == null) {
            throw new IllegalArgumentException("Test not found: " + id);
        }

        TestEntity copy = new TestEntity();
        copy.setName(original.getName() + " (Copy)");
        copy.setDescription(original.getDescription());
        copy.setTestType(original.getTestType());
        copy.setPayloads(new ArrayList<>(original.getPayloads()));

        copy.persist();
        return copy;
    }

    @Transactional
    public void delete(Long id) {
        TestEntity.deleteById(id);
    }

    private List<PayloadEntity> fetchPayloads(List<Long> ids) {
        List<PayloadEntity> payloads = new ArrayList<>();
        for (Long pid : ids) {
            PayloadEntity.findByIdOptional(pid).ifPresent(p -> payloads.add((PayloadEntity) p));
        }
        return payloads;
    }

    @Transactional
    public TestInfo getTestInfo(Long testId) {
        TestEntity test = findById(testId)
            .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        List<Long> payloadIds = test.getPayloads().stream()
            .map(p -> p.id)
            .toList();

        return new TestInfo(test.getTestType(), payloadIds);
    }
}
