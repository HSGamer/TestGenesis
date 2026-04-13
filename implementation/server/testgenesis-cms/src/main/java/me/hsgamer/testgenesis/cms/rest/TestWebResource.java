package me.hsgamer.testgenesis.cms.rest;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.cms.service.PayloadService;
import me.hsgamer.testgenesis.cms.service.TestService;
import me.hsgamer.testgenesis.cms.service.UAPService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.TestManager;
import org.jboss.resteasy.reactive.RestForm;

import java.net.URI;
import java.util.List;

@Path("/tests")
@Slf4j
public class TestWebResource {

    @Inject
    TestService testService;

    @Inject
    PayloadService payloadService;

    @Inject
    UAPService uapService;

    @Inject
    TestManager testManager;

    @Inject
    Template tests_list;

    @Inject
    Template tests_edit;

    @Inject
    Template tests_run;

    @Inject
    Template tests_status;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance list() {
        return tests_list
                .data("tests", testService.listAll())
                .data("sessions", uapService.getTestSessions().values());
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance createForm() {
        return tests_edit.data("test", new TestEntity())
                .data("allPayloads", payloadService.listAll())
                .data("agents", uapService.getAgentGuidedInfos());
    }


    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editForm(@PathParam("id") Long id) {
        TestEntity entity = testService.findById(id)
                .orElseThrow(() -> new NotFoundException("Test not found: " + id));
        return tests_edit.data("test", entity)
                .data("allPayloads", payloadService.listAll())
                .data("agents", uapService.getAgentGuidedInfos());
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
        return tests_run
                .data("test", test)
                .data("agents", uapService.getAgentGuidedInfos());
    }

    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Blocking
    public Uni<Response> start(
            @PathParam("id") Long id,
            @RestForm("agentId") String agentId) {

        return testManager.startTest(id, agentId)
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
        TestSession session = uapService.getTestSessions().get(sessionId);
        if (session == null) {
            throw new NotFoundException("Test session not found: " + sessionId);
        }

        return tests_status.data("session", session);
    }
}
