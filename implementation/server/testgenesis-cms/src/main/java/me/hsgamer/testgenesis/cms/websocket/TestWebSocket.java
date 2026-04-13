package me.hsgamer.testgenesis.cms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.Telemetry;
import me.hsgamer.testgenesis.uap.v1.TestResult;
import me.hsgamer.testgenesis.uap.v1.TestStatus;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@WebSocket(path = "/telemetry/test/{sessionId}")
@Slf4j
public class TestWebSocket {
    private final Map<String, Consumer<Telemetry>> teleSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TestStatus>> statusSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TestResult>> resultSubscriptions = new ConcurrentHashMap<>();

    @Inject
    UAPService uapService;

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(WebSocketConnection connection, @PathParam("sessionId") String sessionId) {
        TestSession session = uapService.getTestSessions().get(sessionId);
        if (session == null) {
            log.warn("WebSocket connection attempt for unknown test session: {}", sessionId);
            connection.sendText("{\"type\": \"ERROR\", \"message\": \"Session not found\"}");
            connection.close();
            return;
        }

        log.info("Client connected to test telemetry for session: {}", sessionId);

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

        Consumer<TestStatus> statusConsumer = status -> {
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

        Consumer<TestResult> resultConsumer = result -> {
            try {
                // Map Protobuf to DTO to avoid Jackson serialization issues with UnknownFields
                ResultDTO dto = mapToResultDTO(result);
                ResultMessage msg = new ResultMessage("RESULT", dto);
                connection.sendText(objectMapper.writeValueAsString(msg)).subscribe().with(v -> {
                }, err -> log.error("WS error", err));
            } catch (Exception e) {
                log.error("Failed to serialize results", e);
            }
        };

        session.addTelemetryConsumer(teleConsumer);
        session.addStatusConsumer(statusConsumer);
        session.addResultConsumer(resultConsumer);

        teleSubscriptions.put(connection.id(), teleConsumer);
        statusSubscriptions.put(connection.id(), statusConsumer);
        resultSubscriptions.put(connection.id(), resultConsumer);
    }

    private ResultDTO mapToResultDTO(TestResult result) {
        return new ResultDTO(
                result.getReportsList().stream().map(r -> new StepReportDTO(
                        r.getStatus().name(),
                        r.getName(),
                        new StepSummaryDTO(
                                new DurationDTO(r.getSummary().getTotalDuration().getSeconds()),
                                r.getSummary().getMetadata().getFieldsMap().entrySet().stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> mapValue(e.getValue())
                                        ))
                        ),
                        r.getAttachmentsList().stream().map(a -> new AttachmentDTO(
                                a.getMimeType(),
                                Base64.getEncoder().encodeToString(a.getData().toByteArray())
                        )).toList()
                )).toList()
        );
    }

    private Object mapValue(com.google.protobuf.Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> value.getStructValue().getFieldsMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> mapValue(e.getValue())));
            case LIST_VALUE -> value.getListValue().getValuesList().stream().map(this::mapValue).toList();
            default -> null;
        };
    }

    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam("sessionId") String sessionId) {
        TestSession session = uapService.getTestSessions().get(sessionId);
        if (session != null) {
            Consumer<Telemetry> teleSub = teleSubscriptions.remove(connection.id());
            if (teleSub != null) session.removeTelemetryConsumer(teleSub);

            Consumer<TestStatus> statusSub = statusSubscriptions.remove(connection.id());
            if (statusSub != null) session.removeStatusConsumer(statusSub);

            Consumer<TestResult> resultSub = resultSubscriptions.remove(connection.id());
            if (resultSub != null) session.removeResultConsumer(resultSub);
        } else {
            teleSubscriptions.remove(connection.id());
            statusSubscriptions.remove(connection.id());
            resultSubscriptions.remove(connection.id());
        }
        log.info("Test WS Connection {} closed.", connection.id());
    }

    public record TelemetryMessage(String type, String level, String message, long timestamp) {
    }

    public record StatusMessage(String type, String state, String message) {
    }

    public record ResultMessage(String type, ResultDTO result) {
    }

    public record ResultDTO(java.util.List<StepReportDTO> reports) {
    }

    public record StepReportDTO(String status, String name, StepSummaryDTO summary, java.util.List<AttachmentDTO> attachments) {
    }

    public record StepSummaryDTO(DurationDTO totalDuration, Map<String, Object> metadata) {
    }

    public record DurationDTO(long seconds) {
    }

    public record AttachmentDTO(String mimeType, String data) {
    }
}
