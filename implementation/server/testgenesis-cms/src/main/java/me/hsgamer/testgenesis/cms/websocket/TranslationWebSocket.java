package me.hsgamer.testgenesis.cms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@WebSocket(path = "/telemetry/translation/{sessionId}")
@Slf4j
public class TranslationWebSocket {
    private final Map<String, Consumer<Telemetry>> teleSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TranslationStatus>> statusSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<List<TranslationSession.GeneratedPayload>>> resultSubscriptions = new ConcurrentHashMap<>();

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
                connection.sendText(objectMapper.writeValueAsString(msg)).subscribe().with(v -> {
                }, err -> log.error("WS error", err));
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
                connection.sendText(objectMapper.writeValueAsString(msg)).subscribe().with(v -> {
                }, err -> log.error("WS error", err));
            } catch (Exception e) {
                log.error("Failed to serialize status", e);
            }
        };

        Consumer<List<TranslationSession.GeneratedPayload>> resultConsumer = payloads -> {
            try {
                ResultMessage msg = new ResultMessage("RESULT", payloads);
                connection.sendText(objectMapper.writeValueAsString(msg)).subscribe().with(v -> {
                }, err -> log.error("WS error", err));
            } catch (Exception e) {
                log.error("Failed to serialize results", e);
            }
        };

        session.addTelemetryConsumer(teleConsumer);
        session.addStatusConsumer(statusConsumer);
        session.addResultPayloadConsumer(resultConsumer);

        teleSubscriptions.put(connection.id(), teleConsumer);
        statusSubscriptions.put(connection.id(), statusConsumer);
        resultSubscriptions.put(connection.id(), resultConsumer);
    }

    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam("sessionId") String sessionId) {
        TranslationSession session = uapService.getTranslationSessions().get(sessionId);
        if (session != null) {
            Consumer<Telemetry> teleSub = teleSubscriptions.remove(connection.id());
            if (teleSub != null) session.removeTelemetryConsumer(teleSub);

            Consumer<TranslationStatus> statusSub = statusSubscriptions.remove(connection.id());
            if (statusSub != null) session.removeStatusConsumer(statusSub);

            Consumer<List<TranslationSession.GeneratedPayload>> resultSub = resultSubscriptions.remove(connection.id());
            if (resultSub != null) session.removeResultPayloadConsumer(resultSub);
        } else {
            teleSubscriptions.remove(connection.id());
            statusSubscriptions.remove(connection.id());
            resultSubscriptions.remove(connection.id());
        }
        log.info("Connection {} closed.", connection.id());
    }

    public record TelemetryMessage(String type, String level, String message, long timestamp) {
    }

    public record StatusMessage(String type, String state, String message) {
    }

    public record ResultMessage(String type, List<TranslationSession.GeneratedPayload> payloads) {
    }
}


