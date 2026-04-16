package me.hsgamer.testgenesis.agent;

import me.hsgamer.testgenesis.client.Agent;

import java.util.logging.Logger;

/**
 * Main entry point for the Java Agent.
 */
public class AgentApp {
    private static final Logger logger = Logger.getLogger(AgentApp.class.getName());

    public static void main(String[] args) {
        String hubUrl = System.getenv("HUB_URL") != null ? System.getenv("HUB_URL") : "http://localhost:9000";
        String clientName = System.getenv("CLIENT_NAME") != null ? System.getenv("CLIENT_NAME") : "__name__-" + (int)(Math.random() * 1000);

        // Simple CLI argument parsing
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--url") && i + 1 < args.length) {
                hubUrl = args[++i];
            } else if (args[i].equals("--name") && i + 1 < args.length) {
                clientName = args[++i];
            }
        }

        logger.info("[__name__] Hub: " + hubUrl);
        logger.info("[__name__] Name: " + clientName);

        Agent agent = new Agent(hubUrl, clientName);

        // Register Processors
        agent.registerTestProcessor(new ExampleProcessor());

        // Handle Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\n[Shutdown] Cleaning up...");
            agent.shutdown();
        }));

        // Start Agent
        agent.start();
    }
}
