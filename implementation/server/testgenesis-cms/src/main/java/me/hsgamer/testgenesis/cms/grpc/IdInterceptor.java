package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class IdInterceptor implements ServerInterceptor {
    public static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SESSION_ID_KEY = Metadata.Key.of("x-session-id", Metadata.ASCII_STRING_MARSHALLER);
            
    public static final Context.Key<String> CLIENT_ID_CONTEXT_KEY = Context.key("clientId");
    public static final Context.Key<String> SESSION_ID_CONTEXT_KEY = Context.key("sessionId");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String clientId = headers.get(CLIENT_ID_KEY);
        String sessionId = headers.get(SESSION_ID_KEY);
        if (clientId == null && sessionId == null) {
            return next.startCall(call, headers);
        }
        Context ctx = Context.current();
        if (clientId != null) {
            ctx.withValue(CLIENT_ID_CONTEXT_KEY, clientId);
        }
        if (sessionId != null) {
            ctx.withValue(SESSION_ID_CONTEXT_KEY, sessionId);
        }
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
