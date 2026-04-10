package me.hsgamer.testgenesis.cms.rest;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.persistence.PayloadEntity;
import me.hsgamer.testgenesis.cms.service.PayloadService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Optional;

@Path("/payloads")
@Slf4j
public class PayloadWebResource {

    @Inject
    PayloadService payloadService;

    @Inject
    Template payloads_list;

    @Inject
    Template payloads_edit;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return payloads_list.data("payloads", payloadService.listAll());
    }

    @GET
    @Path("/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance createForm() {
        return payloads_edit.data("payload", new PayloadEntity());
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editForm(@PathParam("id") Long id) {
        PayloadEntity entity = payloadService.findById(id)
                .orElseThrow(() -> new NotFoundException("Payload not found: " + id));
        return payloads_edit.data("payload", entity);
    }

    @POST
    @Path("/save")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response save(
            @RestForm("id") Long id,
            @RestForm("name") String name,
            @RestForm("description") String description,
            @RestForm("type") String type,
            @RestForm("metadata") String metadata,
            @RestForm("attachment") FileUpload attachment) {

        PayloadEntity entity = new PayloadEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setType(type);
        entity.setMetadata(metadata);

        if (attachment != null && attachment.size() > 0) {
            try {
                entity.setAttachmentName(attachment.fileName());
                entity.setAttachmentMimeType(attachment.contentType());
                entity.setAttachmentData(Files.readAllBytes(attachment.filePath()));
            } catch (IOException e) {
                log.error("Failed to read uploaded file", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to upload attachment").build();
            }
        }

        if (id != null) {
            payloadService.update(id, entity);
        } else {
            payloadService.create(entity);
        }

        return Response.seeOther(URI.create("/payloads")).build();
    }

    @POST
    @Path("/{id}/delete")
    public Response delete(@PathParam("id") Long id) {
        payloadService.delete(id);
        return Response.seeOther(URI.create("/payloads")).build();
    }

    @GET
    @Path("/{id}/attachment")
    public Response downloadAttachment(@PathParam("id") Long id) {
        PayloadEntity entity = payloadService.findById(id)
                .orElseThrow(() -> new NotFoundException("Payload not found: " + id));
        
        if (entity.getAttachmentData() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(entity.getAttachmentData())
                .type(entity.getAttachmentMimeType())
                .header("Content-Disposition", "attachment; filename=\"" + entity.getAttachmentName() + "\"")
                .build();
    }
}
