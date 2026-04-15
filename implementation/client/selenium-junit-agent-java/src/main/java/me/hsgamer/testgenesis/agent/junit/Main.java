package me.hsgamer.testgenesis.agent.junit;

import io.github.bonigarcia.wdm.WebDriverManager;
import me.hsgamer.testgenesis.client.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String hubUrl = System.getenv("HUB_URL");
        if (hubUrl == null) hubUrl = "http://localhost:9000";

        String agentName = System.getenv("AGENT_NAME");
        if (agentName == null) agentName = "SeleniumJUnitAgent-" + (int)(Math.random() * 1000);

        String browserType = System.getenv("BROWSER_TYPE");
        if (browserType == null) browserType = "chrome";

        logger.info("[Main] Hub: {}", hubUrl);
        logger.info("[Main] Name: {}", agentName);
        logger.info("[Main] Browser Type: {}", browserType);

        // Pre-setup WebDriver binaries programmatically
        try {
            logger.info("[Main] Setting up WebDriver for {}...", browserType);
            WebDriverManager.getInstance(browserType).setup();
        } catch (Exception e) {
            logger.warn("[Main] Failed to setup WebDriverManager. Tests might fail if drivers are not in PATH.", e);
        }

        Agent agent = new Agent(hubUrl, agentName);
        agent.registerTestProcessor(new SeleniumJUnitProcessor());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("[Main] Shutting down agent...");
            agent.shutdown();
        }));

        try {
            agent.start();
        } catch (Exception e) {
            logger.error("[Main] Agent crashed", e);
            System.exit(1);
        }
    }
}
