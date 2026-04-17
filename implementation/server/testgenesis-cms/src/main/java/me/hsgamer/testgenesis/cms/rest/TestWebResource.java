package me.hsgamer.testgenesis.cms.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.BatchStatus;
import me.hsgamer.testgenesis.cms.core.TestBatchSession;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.cms.service.*;
import me.hsgamer.testgenesis.cms.util.ProtoUtil;
import me.hsgamer.testgenesis.uap.v1.Attachment;
import me.hsgamer.testgenesis.uap.v1.TestResult;
import org.jboss.resteasy.reactive.RestForm;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/tests")
@Slf4j
public class TestWebResource {

    @Inject
    TestService testService;

    @Inject
    PayloadService payloadService;

    @Inject
    AgentManager agentManager;

    @Inject
    TestSessionManager testSessionManager;

    @Inject
    BatchTestManager batchTestManager;

    @Inject
    Template tests_list;

    @Inject
    Template tests_edit;

    @Inject
    Template tests_run;

    @Inject
    Template tests_status;

    @Inject
    Template tests_batch_status;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance list() {
        return tests_list
            .data("tests", testService.listAll())
            .data("sessions", testSessionManager.getTestSessions())
            .data("batches", batchTestManager.getBatchSessions());
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance createForm() {
        return tests_edit.data("test", new TestEntity())
            .data("allPayloads", payloadService.listAll())
            .data("agents", agentManager.getAgentInfos());
    }


    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editForm(@PathParam("id") Long id) {
        TestEntity entity = testService.findById(id)
            .orElseThrow(() -> new NotFoundException("Test not found: " + id));
        return tests_edit.data("test", entity)
            .data("allPayloads", payloadService.listAll())
            .data("agents", agentManager.getAgentInfos());
    }


    @POST
    @Path("/save")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(
        @RestForm("id") Long id,
        @RestForm("name") String name,
        @RestForm("description") String description,
        @RestForm("testType") String testType,
        @RestForm("payloadIds") List<Long> payloadIds) {

        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setTestType(testType);

        if (id != null) {
            testService.update(id, entity, payloadIds);
        } else {
            testService.create(entity, payloadIds);
        }

        return Response.seeOther(URI.create("/tests")).build();
    }

    @POST
    @Path("/{id}/copy")
    public Response copy(@PathParam("id") Long id) {
        TestEntity copy = testService.copy(id);
        return Response.seeOther(URI.create("/tests/" + copy.id + "/edit")).build();
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        testService.delete(id);
        return Response.seeOther(URI.create("/tests")).build();
    }

    @GET
    @Path("/{id}/run")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance runForm(@PathParam("id") Long id) {
        TestEntity test = testService.findById(id)
            .orElseThrow(() -> new NotFoundException("Test not found: " + id));

        Set<Long> linkedIds = test.getPayloads().stream()
            .map(p -> p.id)
            .collect(java.util.stream.Collectors.toSet());

        List<PayloadEntity> extraPayloads = payloadService.listAll().stream()
            .filter(p -> !linkedIds.contains(p.id))
            .toList();

        return tests_run
            .data("test", test)
            .data("agents", agentManager.getAgentInfos())
            .data("extraPayloads", extraPayloads);
    }

    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Blocking
    public Uni<Response> start(
        @PathParam("id") Long id,
        @RestForm("agentIds") List<String> agentIds,
        @RestForm("extraPayloadIds") java.util.List<Long> extraPayloadIds,
        @RestForm("iterations") @DefaultValue("1") int iterations,
        @RestForm("parallel") @DefaultValue("false") boolean parallel) {

        if (agentIds == null || agentIds.isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("No agents selected").build());
        }

        if (iterations > 1 || agentIds.size() > 1) {
            String batchId = batchTestManager.startBatchTest(id, agentIds, extraPayloadIds, iterations, parallel);
            return Uni.createFrom().item(Response.seeOther(URI.create("/tests/batch/" + batchId + "/status")).build());
        }

        String agentId = agentIds.get(0);
        return testSessionManager.startTest(id, agentId, extraPayloadIds)
            .map(result -> {
                if (result.accepted()) {
                    return Response.seeOther(URI.create("/tests/" + result.session().getSessionId() + "/status")).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Agent rejected test: " + result.reason())
                        .build();
                }
            });
    }

    @GET
    @Path("/{sessionId}/status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance status(@PathParam("sessionId") String sessionId) {
        TestSession session = testSessionManager.getTestSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Test session not found: " + sessionId));

        return tests_status.data("session", session);
    }

    @GET
    @Path("/batch/{batchId}/status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance batchStatus(@PathParam("batchId") String batchId) {
        TestBatchSession batch = batchTestManager.getBatchSession(batchId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

        return tests_batch_status.data("batch", batch);
    }

    @POST
    @Path("/batch/{batchId}/cancel")
    @Blocking
    public Response cancelBatch(@PathParam("batchId") String batchId) {
        batchTestManager.getBatchSession(batchId).ifPresent(batch -> {
            batch.setStatus(BatchStatus.CANCELLED);
        });
        return Response.seeOther(URI.create("/tests/batch/" + batchId + "/status")).build();
    }

    @GET
    @Path("/{sessionId}/report/json")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response downloadReport(@PathParam("sessionId") String sessionId, @QueryParam("full") @DefaultValue("false") boolean full) {
        TestSession session = testSessionManager.getTestSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Test session not found: " + sessionId));

        if (session.getResult() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Result not available").build();
        }

        var result = session.getResult();
        if (!full) {
            var builder = result.toBuilder();
            for (int i = 0; i < builder.getAttachmentsCount(); i++) {
                builder.getAttachmentsBuilder(i).clearData();
            }
            result = builder.build();
        }

        String json = ProtoUtil.toJson(result);
        return Response.ok(json)
            .header("Content-Disposition", "attachment; filename=\"report-" + (full ? "full-" : "") + sessionId + ".json\"")
            .build();
    }

    @GET
    @Path("/batch/{batchId}/report/json")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response downloadBatchReport(@PathParam("batchId") String batchId, @QueryParam("full") @DefaultValue("false") boolean full) {
        TestBatchSession batch = batchTestManager.getBatchSession(batchId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

        Map<String, Object> report = new HashMap<>();
        report.put("batchId", batch.getBatchId());
        report.put("testName", batch.getTestName());
        report.put("status", batch.getStatus());
        report.put("iterations", batch.getTotalIterations());
        report.put("completed", batch.getCompletedCount());

        List<Map<String, Object>> results = batch.getSessions().stream().map(s -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("agentId", s.getAgentId());
            entry.put("agentName", s.getAgentName());
            entry.put("sessionId", s.getSessionId());
            entry.put("status", s.getStatus() != null ? s.getStatus().getState().name() : "PENDING");
            if (s.getResult() != null) {
                try {
                    var sessionResult = s.getResult();
                    if (!full) {
                        var builder = sessionResult.toBuilder();
                        for (int i = 0; i < builder.getAttachmentsCount(); i++) {
                            builder.getAttachmentsBuilder(i).clearData();
                        }
                        sessionResult = builder.build();
                    }
                    entry.put("result", new ObjectMapper().readTree(ProtoUtil.toJson(sessionResult)));
                } catch (Exception e) {
                    entry.put("result", null);
                }
            }
            return entry;
        }).collect(Collectors.toList());

        report.put("sessions", results);

        return Response.ok(report)
            .header("Content-Disposition", "attachment; filename=\"batch-report-" + (full ? "full-" : "") + batchId + ".json\"")
            .build();
    }

    @GET
    @Path("/{sessionId}/attachments/{index}")
    @Blocking
    public Response getAttachment(@PathParam("sessionId") String sessionId, @PathParam("index") int index) {
        TestSession session = testSessionManager.getTestSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Test session not found: " + sessionId));

        TestResult result = session.getResult();
        if (result == null || index < 0 || index >= result.getAttachmentsCount()) {
            throw new NotFoundException("Attachment not found");
        }

        Attachment attachment = result.getAttachments(index);
        return Response.ok(attachment.getData().toByteArray())
            .type(attachment.getMimeType())
            .header("Content-Disposition", "attachment; filename=\"" + attachment.getName() + "\"")
            .build();
    }
}
