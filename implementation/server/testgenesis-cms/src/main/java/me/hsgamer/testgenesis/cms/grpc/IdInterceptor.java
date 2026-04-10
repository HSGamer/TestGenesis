package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import me.hsgamer.testgenesis.cms.service.UAPService;

@ApplicationScoped
@GlobalInterceptor
public class IdInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String clientId = headers.get(UAPService.CLIENT_ID_KEY);
        String sessionId = headers.get(UAPService.SESSION_ID_KEY);

        if (clientId == null && sessionId == null) {
            return next.startCall(call, headers);
        }

        Context ctx = Context.current();
        if (clientId != null) {
            ctx = ctx.withValue(UAPService.CLIENT_ID_CTX, clientId);
        }
        if (sessionId != null) {
            ctx = ctx.withValue(UAPService.SESSION_ID_CTX, sessionId);
        }
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
