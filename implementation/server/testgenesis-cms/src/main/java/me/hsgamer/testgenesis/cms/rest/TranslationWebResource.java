package me.hsgamer.testgenesis.cms.rest;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.service.PayloadService;
import me.hsgamer.testgenesis.cms.service.TranslationManager;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.Payload;
import org.jboss.resteasy.reactive.RestForm;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Path("/translations")
@Slf4j
@ApplicationScoped
public class TranslationWebResource {

    @Inject
    UAPService uapService;

    @Inject
    PayloadService payloadService;

    @Inject
    TranslationManager translationManager;

    @Inject
    Template translations_new;

    @Inject
    Template translations_status;

    @Inject
    Template translations_index;

    @Inject
    Template translations_save_payload;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance list() {
        return translations_index.data("sessions", uapService.getTranslationSessions());
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance createForm() {
        return translations_new
            .data("agents", uapService.getAgentTranslationInfos())
            .data("allPayloads", payloadService.listAll());
    }

    @POST
    @Path("/start")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Blocking
    public Uni<Response> start(
        @RestForm("agentId") String agentId,
        @RestForm("type") String type,
        @RestForm("payloadIds") List<Long> payloadIds) {

        if (payloadIds == null || payloadIds.isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity("No source payloads selected")
                .build());
        }

        List<Payload> sourcePayloads = payloadIds.stream()
            .map(id -> payloadService.findById(id).orElseThrow(() -> new NotFoundException("Payload not found: " + id)))
            .map(PayloadEntity::toProto)
            .toList();

        return translationManager.startTranslation(agentId, type, sourcePayloads)
            .map(result -> {
                if (result.accepted()) {
                    return Response.seeOther(URI.create("/translations/" + result.session().getSessionId() + "/status")).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Agent rejected translation: " + result.reason())
                        .build();
                }
            });
    }

    @GET
    @Path("/{sessionId}/status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance status(@PathParam("sessionId") String sessionId) {
        TranslationSession session = uapService.getTranslationSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Translation session not found: " + sessionId));

        List<GeneratedPayloadInfo> generatedItems = new ArrayList<>();
        List<Payload> payloads = session.getRawPayloads();
        for (int i = 0; i < payloads.size(); i++) {
            Payload p = payloads.get(i);
            String name = p.hasAttachment() ? p.getAttachment().getName() : "unnamed";
            var saved = payloadService.findByOrigin(sessionId, name);
            generatedItems.add(new GeneratedPayloadInfo(i, name, p.getType(), saved.map(e -> e.id).orElse(null)));
        }

        return translations_status
            .data("session", session)
            .data("generatedItems", generatedItems);
    }

    @GET
    @Path("/{sessionId}/payloads/{index}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("sessionId") String sessionId, @PathParam("index") int index) {
        TranslationSession session = uapService.getTranslationSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));
        if (index < 0 || index >= session.getRawPayloads().size()) {
            throw new NotFoundException("Payload index out of range");
        }
        Payload payload = session.getRawPayloads().get(index);
        byte[] data = payload.getAttachment().getData().toByteArray();
        return Response.ok(data)
            .header("Content-Disposition", "attachment; filename=\"" + payload.getAttachment().getName() + "\"")
            .type(payload.getAttachment().getMimeType())
            .build();
    }

    @GET
    @Path("/{sessionId}/payloads/{index}/save-form")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance saveForm(@PathParam("sessionId") String sessionId, @PathParam("index") int index) {
        TranslationSession session = uapService.getTranslationSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));
        if (index < 0 || index >= session.getRawPayloads().size()) {
            throw new NotFoundException("Payload index out of range");
        }
        Payload payload = session.getRawPayloads().get(index);
        String defaultName = payload.hasAttachment() ? "Translated: " + payload.getAttachment().getName() : "Translated: " + payload.getType();
        return translations_save_payload
            .data("sessionId", sessionId)
            .data("index", index)
            .data("defaultName", defaultName)
            .data("type", payload.getType());
    }

    @POST
    @Path("/save-payload")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Blocking
    public Response savePayload(
        @RestForm("sessionId") String sessionId,
        @RestForm("index") int index,
        @RestForm("name") String name,
        @RestForm("description") String description) {

        TranslationSession session = uapService.getTranslationSession(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));
        if (index < 0 || index >= session.getRawPayloads().size()) {
            throw new NotFoundException("Payload index out of range");
        }
        Payload payload = session.getRawPayloads().get(index);
        payloadService.savePayload(sessionId, payload, name, description);

        return Response.seeOther(URI.create("/translations/" + sessionId + "/status")).build();
    }

    public record GeneratedPayloadInfo(int index, String name, String type, Long databaseId) {
    }
}
