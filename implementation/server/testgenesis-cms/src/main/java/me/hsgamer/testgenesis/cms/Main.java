package me.hsgamer.testgenesis.cms;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import me.hsgamer.testgenesis.cms.db.repository.PayloadRepository;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import me.hsgamer.testgenesis.cms.db.repository.TestResultRepository;
import me.hsgamer.testgenesis.cms.db.repository.TranslationRepository;
import me.hsgamer.testgenesis.cms.db.repository.TranslationResultRepository;
import me.hsgamer.testgenesis.cms.service.TestExecutionService;
import me.hsgamer.testgenesis.cms.service.TranslationService;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.cms.web.DashboardController;
import me.hsgamer.testgenesis.cms.web.ExecutionController;
import me.hsgamer.testgenesis.cms.web.TestController;
import me.hsgamer.testgenesis.cms.web.TranslationController;

/**
 * Main entry point for the TestGenesis CMS.
 * Uses a hybrid wiring model:
 * - Data layer (Repositories) managed by Helidon Service Registry.
 * - Application layer (Services, Managers, Controllers) manually wired.
 */
public class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();

        // Fetch Repositories from Registry
        TestRepository testRepo = Services.get(TestRepository.class);
        TestResultRepository resultRepo = Services.get(TestResultRepository.class);
        PayloadRepository payloadRepo = Services.get(PayloadRepository.class);
        TranslationRepository translationRepo = Services.get(TranslationRepository.class);
        TranslationResultRepository translationResultRepo = Services.get(TranslationResultRepository.class);

        // Manually Instantiate Application Components
        TestExecutionService executionManager = new TestExecutionService(testRepo, resultRepo);
        TranslationService translationService = new TranslationService(translationRepo, translationResultRepo, testRepo, payloadRepo);
        
        DashboardController dashboardController = new DashboardController(resultRepo, testRepo);
        TestController testController = new TestController(testRepo);
        ExecutionController executionController = new ExecutionController(executionManager);
        TranslationController translationController = new TranslationController(translationService, translationRepo, testRepo);

        // Start WebServer
        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addRouting(UAPService.INSTANCE.routing()) // gRPC Hub
                .addFeature(
                        StaticContentFeature.builder().addClasspath(builder -> builder.location("/static").context("/static")).build()
                )
                .routing(routing -> routing
                        .register("/", dashboardController)
                        .register("/", testController)
                        .register("/", executionController)
                        .register("/", translationController)
                )
                .build()
                .start();

        System.out.println("TestGenesis CMS is up! gRPC and Web on port " + server.port());
    }
}