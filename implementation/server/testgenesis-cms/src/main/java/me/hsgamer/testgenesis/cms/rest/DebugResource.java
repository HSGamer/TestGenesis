package me.hsgamer.testgenesis.cms.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import me.hsgamer.testgenesis.cms.service.UAPService;

import java.util.Map;

@Path("/debug/agents")
public class DebugResource {
    @Inject
    UAPService uapService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getAgents() {
        Map<String, String> result = new java.util.HashMap<>();
        uapService.getAgents().forEach((id, agent) -> result.put(id, agent.displayName()));
        return result;
    }

}
