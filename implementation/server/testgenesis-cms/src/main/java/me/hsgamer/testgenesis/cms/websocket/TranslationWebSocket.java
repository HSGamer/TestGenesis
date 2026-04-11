package me.hsgamer.testgenesis.cms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@WebSocket(path = "/telemetry/translation/{sessionId}")
@Slf4j
public class TranslationWebSocket {

    private final Map<String, Consumer<Telemetry>> teleSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TranslationStatus>> statusSubscriptions = new ConcurrentHashMap<>();

    @Inject
    UAPService uapService;

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(WebSocketConnection connection, @PathParam("sessionId") String sessionId) {
        TranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session == null) {
            log.warn("WebSocket connection attempt for unknown session: {}", sessionId);
            connection.sendText("{\"type\": \"ERROR\", \"message\": \"Session not found\"}");
            connection.close();
            return;
        }

        log.info("Client connected to telemetry for session: {}", sessionId);

        Consumer<Telemetry> teleConsumer = telemetry -> {
            try {
                TelemetryMessage msg = new TelemetryMessage(
                        "TELEMETRY",
                        telemetry.getSeverity().name(),
                        telemetry.getMessage(),
                        telemetry.getTimestamp().getSeconds() * 1000 + telemetry.getTimestamp().getNanos() / 1000000);
                connection.sendText(objectMapper.writeValueAsString(msg));
            } catch (Exception e) {
                log.error("Failed to serialize telemetry", e);
            }
        };

        Consumer<TranslationStatus> statusConsumer = status -> {
            try {
                StatusMessage msg = new StatusMessage(
                        "STATUS",
                        status.getState().name(),
                        status.getMessage());
                connection.sendText(objectMapper.writeValueAsString(msg));
            } catch (Exception e) {
                log.error("Failed to serialize status", e);
            }
        };

        session.addTelemetryConsumer(teleConsumer);
        session.addStatusConsumer(statusConsumer);

        teleSubscriptions.put(connection.id(), teleConsumer);
        statusSubscriptions.put(connection.id(), statusConsumer);
    }

    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam("sessionId") String sessionId) {
        TranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session != null) {
            Consumer<Telemetry> teleSub = teleSubscriptions.remove(connection.id());
            if (teleSub != null) {
                session.removeTelemetryConsumer(teleSub);
            }
            Consumer<TranslationStatus> statusSub = statusSubscriptions.remove(connection.id());
            if (statusSub != null) {
                session.removeStatusConsumer(statusSub);
            }
        } else {
            teleSubscriptions.remove(connection.id());
            statusSubscriptions.remove(connection.id());
        }
        log.info("Connection {} closed. Telemetry subscription cancelled.", connection.id());
    }

    public record TelemetryMessage(String type, String level, String message, long timestamp) {}
    public record StatusMessage(String type, String state, String message) {}
}


