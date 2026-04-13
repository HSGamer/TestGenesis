package me.hsgamer.testgenesis.cms.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.TestTicket;
import me.hsgamer.testgenesis.cms.core.TestTicketResult;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.uap.v1.Payload;

import java.util.List;

@ApplicationScoped
public class TestManager {

    @Inject
    UAPService uapService;

    @Inject
    TestService testService;

    public Uni<TestTicketResult> startTest(Long testId, String agentId) {
        TestEntity test = testService.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        List<Payload> payloads = test.getPayloads().stream()
                .map(PayloadEntity::toProto)
                .toList();

        TestTicket ticket = new TestTicket(test.getTestType(), payloads);

        return uapService.registerTest(agentId, ticket);
    }
}
