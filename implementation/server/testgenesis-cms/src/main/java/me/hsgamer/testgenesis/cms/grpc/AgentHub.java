package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;

import java.util.UUID;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class AgentHub extends MutinyAgentHubGrpc.AgentHubImplBase {
    private final UAPService uapService;

    @Override
    public Uni<AgentRegistrationResponse> register(AgentRegistration request) {
        String agentId = UUID.randomUUID().toString();
        UAPService.AgentImpl agent = new UAPService.AgentImpl(request.getDisplayName(), request.getCapabilitiesList());
        uapService.getInternalAgents().put(agentId, agent);
        log.info("Registered agent: {} ({})", agentId, request.getDisplayName());
        return Uni.createFrom().item(AgentRegistrationResponse.newBuilder().setClientId(agentId).build());
    }

    @Override
    public Multi<ListenResponse> listen(Multi<ListenRequest> requests) {
        String agentId = UAPService.CLIENT_ID_CTX.get();
        if (agentId == null) {
            return Multi.createFrom().failure(Status.UNAUTHENTICATED
                    .withDescription("Missing required header: x-client-id")
                    .asRuntimeException());
        }

        UAPService.AgentImpl agent = uapService.getInternalAgents().get(agentId);
        if (agent == null) {
            return Multi.createFrom().failure(Status.NOT_FOUND
                    .withDescription("Client ID is not registered")
                    .asRuntimeException());
        }

        log.info("Agent {} ({}) connected to Listen stream.", agentId, agent.displayName());

        requests.subscribe().with(
                value -> {
                    switch (value.getEventCase()) {
                        case READY -> log.info("Agent {} ({}) signaled ready.", agentId, agent.displayName());
                        case SESSION_ACCEPTANCE -> {
                            SessionAcceptance acceptance = value.getSessionAcceptance();
                            var consumer = uapService.getPendingSessionAcceptances().get(acceptance.getSessionId());
                            if (consumer != null) consumer.accept(acceptance);
                        }

                        case EVENT_NOT_SET -> {
                        }
                    }
                },
                failure -> {
                    log.error("Listen stream failed for agent {} ({}): {}", agentId, agent.displayName(), failure.getMessage());
                    uapService.cleanupAgent(agentId);
                },
                () -> {
                    log.info("Listen stream disconnected by agent {} ({}).", agentId, agent.displayName());
                    uapService.cleanupAgent(agentId);
                }
        );

        return Multi.createFrom().emitter(emitter -> {
            agent.setStreamDispatcher(response -> {
                emitter.emit(response);
            });
        });
    }
}

