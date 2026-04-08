package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.*;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GrpcService
@Slf4j
public class AgentHubImpl extends AgentHubGrpc.AgentHubImplBase {
    private final UAPService uapService;

    public AgentHubImpl(UAPService uapService) {
        this.uapService = uapService;
    }

    @Override
    public void register(AgentRegistration request, StreamObserver<AgentRegistrationResponse> responseObserver) {
        String agentId = UUID.randomUUID().toString();
        UAPService.AgentImpl agent = new UAPService.AgentImpl(request.getDisplayName(), request.getCapabilitiesList(), new AtomicReference<>());
        uapService.getInternalAgents().put(agentId, agent);
        log.info("Registered agent: {} ({})", agentId, request.getDisplayName());
        responseObserver.onNext(AgentRegistrationResponse.newBuilder().setClientId(agentId).build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ListenRequest> listen(StreamObserver<ListenResponse> responseObserver) {
        String agentId = UAPService.CLIENT_ID_CTX.get();
        if (agentId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Missing required header: x-client-id")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }
        UAPService.AgentImpl agent = uapService.getInternalAgents().get(agentId);
        if (agent == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Client ID is not registered")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }
        if (agent.listenerRef().get() != null) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription("A client with this ID is already listening")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        agent.listenerRef().set(responseObserver);
        log.info("Agent {} ({}) connected to Listen stream.", agentId, agent.displayName());

        return new StreamObserver<>() {
            @Override
            public void onNext(ListenRequest value) {
                if (value.hasJobAcceptance()) {
                    JobAcceptance acceptance = value.getJobAcceptance();
                    Consumer<JobAcceptance> consumer = uapService.getPendingJobAcceptances().get(acceptance.getSessionId());
                    if (consumer != null) {
                        consumer.accept(acceptance);
                    }
                } else if (value.hasTranslationAcceptance()) {
                    TranslationAcceptance acceptance = value.getTranslationAcceptance();
                    Consumer<TranslationAcceptance> consumer = uapService.getPendingTranslationAcceptances().get(acceptance.getSessionId());
                    if (consumer != null) {
                        consumer.accept(acceptance);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Listen stream error from Agent {} ({})", agentId, agent.displayName(), t);
                uapService.getInternalAgents().remove(agentId);
            }

            @Override
            public void onCompleted() {
                log.info("Agent {} ({}) disconnected.", agentId, agent.displayName());
                uapService.getInternalAgents().remove(agentId);
                responseObserver.onCompleted();
            }
        };
    }

    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    }
}
