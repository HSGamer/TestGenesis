package me.hsgamer.testgenesis.cms;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import me.hsgamer.testgenesis.cms.grpc.IdInterceptor;

/**
 * TestGenesis CMS — Central Management Server.
 * <p>
 * This server hosts the UAP gRPC services (AgentHub, JobHub, TranslationHub)
 * for orchestrating a distributed fleet of Execution Agents.
 */
public class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addRouting(grpcRouting())
                .build()
                .start();

        System.out.println("TestGenesis CMS is up! gRPC on port " + server.port());
    }

    /**
     * Build the gRPC routing for UAP protocol services.
     */
    static GrpcRouting.Builder grpcRouting() {
        return GrpcRouting.builder()
                .intercept(new IdInterceptor());
    }
}