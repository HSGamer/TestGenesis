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

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance list() {
        return translations_index.data("sessions", uapService.getTranslationSessions().values());
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


    public record GeneratedPayloadInfo(Long id, String name, boolean removed) {}

    @GET
    @Path("/{sessionId}/status")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance status(@PathParam("sessionId") String sessionId) {
        TranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session == null) {
            throw new NotFoundException("Translation session not found: " + sessionId);
        }

        List<GeneratedPayloadInfo> generatedEntities = session.getResultPayloads().stream()
                .map(p -> payloadService.findById(p.id())
                        .map(entity -> new GeneratedPayloadInfo(p.id(), entity.name, false))
                        .orElseGet(() -> new GeneratedPayloadInfo(p.id(), null, true)))
                .toList();

        return translations_status
                .data("session", session)
                .data("generatedEntities", generatedEntities);
    }


}
