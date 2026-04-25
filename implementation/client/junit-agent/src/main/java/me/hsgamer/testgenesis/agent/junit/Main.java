package me.hsgamer.testgenesis.agent.junit;

import me.hsgamer.testgenesis.client.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String hubUrl = Optional.ofNullable(System.getenv("HUB_URL")).orElse("http://localhost:9000");
        String agentName = Optional.ofNullable(System.getenv("CLIENT_NAME")).orElse("JUnitAgent-" + (int) (Math.random() * 1000));

        List<String> defaultDeps = parseLines(System.getenv("JUNIT_DEPENDENCIES"));
        logger.info("[Main] Hub: {} | Name: {} | Default deps: {}", hubUrl, agentName, defaultDeps);

        DependencyResolver resolver = new DependencyResolver();

        List<File> defaultJars = List.of();
        if (!defaultDeps.isEmpty()) {
            logger.info("[Main] Pre-resolving default dependencies...");
            defaultJars = resolver.resolve(defaultDeps);
            logger.info("[Main] Pre-resolved {} JARs", defaultJars.size());
        }

        Agent agent = new Agent(hubUrl, agentName);
        agent.registerTestProcessor(new JUnitProcessor(defaultJars, resolver));
        Runtime.getRuntime().addShutdownHook(new Thread(agent::shutdown));

        try {
            agent.start();
        } catch (Exception e) {
            logger.error("[Main] Agent crashed", e);
            System.exit(1);
        }
    }

    private static List<String> parseLines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        // Split by real newlines or the literal string "\n"
        for (String line : value.split("\\r?\\n|\\\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
