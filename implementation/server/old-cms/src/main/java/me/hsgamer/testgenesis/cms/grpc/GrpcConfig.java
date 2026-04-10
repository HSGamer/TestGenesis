package me.hsgamer.testgenesis.cms.grpc;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.service.UAPService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

@Configuration
@Slf4j
public class GrpcConfig {
    @Bean
    @GlobalServerInterceptor
    public ServerInterceptor idInterceptor() {
        return new ServerInterceptor() {
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
        };
    }
}
