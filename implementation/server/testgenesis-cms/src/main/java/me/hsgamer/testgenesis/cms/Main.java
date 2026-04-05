package me.hsgamer.testgenesis.cms;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import me.hsgamer.testgenesis.cms.service.UAPService;

public class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addRouting(UAPService.INSTANCE.routing())
                .build()
                .start();

        System.out.println("TestGenesis CMS is up! gRPC on port " + server.port());
    }
}