package me.hsgamer.testgenesis.cms.rest;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.persistence.TestEntity;
import me.hsgamer.testgenesis.cms.service.PayloadService;
import me.hsgamer.testgenesis.cms.service.TestService;
import me.hsgamer.testgenesis.cms.service.UAPService;

import org.jboss.resteasy.reactive.RestForm;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

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
    Template tests_list;

    @Inject
    Template tests_edit;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return tests_list.data("tests", testService.listAll());
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
}
