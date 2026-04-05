package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.stub.StreamObserver;
import me.hsgamer.testgenesis.uap.v1.*;

public class AgentService extends AgentHubGrpc.AgentHubImplBase {
    @Override
    public void register(AgentRegistration request, StreamObserver<AgentRegistrationResponse> responseObserver) {
        super.register(request, responseObserver);
    }

    @Override
    public StreamObserver<ListenRequest> listen(StreamObserver<ListenResponse> responseObserver) {
        return super.listen(responseObserver);
    }
}
