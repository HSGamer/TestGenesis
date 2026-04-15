package me.hsgamer.testgenesis.agent.selenium.junit;

import me.hsgamer.testgenesis.client.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static void main(String[] args) {
        String hubUrl = Optional.ofNullable(System.getenv("HUB_URL")).orElse("http://localhost:9000");
        String agentName = Optional.ofNullable(System.getenv("AGENT_NAME")).orElse("SeleniumJUnitAgent-" + (int) (Math.random() * 1000));
        String browserType = Optional.ofNullable(System.getenv("BROWSER_TYPE")).orElse("chrome");
        String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");

        logger.info("[Main] Hub: {} | Name: {} | Browser: {}", hubUrl, agentName, browserType);
        if (remoteUrl != null) {
            logger.info("[Main] Remote WebDriver mode enabled: {}", remoteUrl);
            System.setProperty("webdriver.remote.url", remoteUrl);
        }

        String detectedPath = "chrome".equalsIgnoreCase(browserType) ? detectAndSetChromeBinary() : null;

        try {
            logger.info("[Main] Setting up WebDriverManager for {}...", browserType);
            io.github.bonigarcia.wdm.WebDriverManager wdm = io.github.bonigarcia.wdm.WebDriverManager.getInstance(browserType);
            if (detectedPath != null) wdm.browserBinary(detectedPath);
            wdm.setup();
        } catch (Exception e) {
            logger.warn("[Main] WebDriverManager setup failed: {}", e.getMessage());
        }

        Agent agent = new Agent(hubUrl, agentName);
        agent.registerTestProcessor(new SeleniumJUnitProcessor());
        Runtime.getRuntime().addShutdownHook(new Thread(agent::shutdown));

        try {
            agent.start();
        } catch (Exception e) {
            logger.error("[Main] Agent crashed", e);
            System.exit(1);
        }
    }

    private static String detectAndSetChromeBinary() {
        return Stream.of(
                System.getProperty("webdriver.chrome.binary"),
                System.getenv("CHROME_BINARY"),
                findBinary("thorium-browser-avx"),
                findBinary("thorium-browser"),
                findBinary("google-chrome-stable"),
                findBinary("chromium")
            ).filter(path -> path != null && !path.isEmpty())
            .findFirst()
            .map(path -> {
                logger.info("[Main] Using browser binary: {}", path);
                System.setProperty("webdriver.chrome.binary", path);
                System.setProperty("CHROME_BIN", path);
                return path;
            }).orElseGet(() -> {
                logger.info("[Main] Using default Selenium browser discovery");
                return null;
            });
    }

    private static String findBinary(String name) {
        try {
            Process process = new ProcessBuilder("which", name).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty() && new File(line).canExecute()) return line;
            }
        } catch (Exception ignored) {
        }

        // Fixed path fallbacks
        return Stream.of("/usr/bin/" + name, "/opt/" + name + "/" + name)
            .filter(p -> new File(p).canExecute())
            .findFirst().orElse(null);
    }
}
