package me.hsgamer.testgenesis.cms.websocket;

import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.uap.v1.TestResult;
import me.hsgamer.testgenesis.uap.v1.TestStatus;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@WebSocket(path = "/telemetry/test/{sessionId}")
public class TestWebSocket extends BaseWebSocket<TestSession> {
    @Inject
    UAPService uapService;

    @Override
    protected Optional<TestSession> getSession(String id) {
        return uapService.getTestSession(id);
    }

    @OnOpen
    public void onOpen(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onOpenBase(conn, id, s -> {
            Consumer<TestStatus> sc = status -> send(conn, WSMessage.StatusMsg.from(status));
            s.addStatusConsumer(sc);
            addCleanup(conn, () -> s.removeStatusConsumer(sc));

            Consumer<TestResult> rc = result -> send(conn, WSMessage.ResultMsg.from(mapResult(result)));
            s.addResultConsumer(rc);
            addCleanup(conn, () -> s.removeResultConsumer(rc));
        });
    }

    @OnClose
    public void onClose(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onCloseBase(conn, id);
    }

    private ResultDTO mapResult(TestResult r) {
        return new ResultDTO(r.getReportsList().stream().map(report -> new StepReportDTO(
                report.getStatus().name(), report.getName(),
                new StepSummaryDTO(report.getSummary().getTotalDuration().getSeconds(),
                        report.getSummary().getMetadata().getFieldsMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> mapValue(e.getValue())))),
                report.getAttachmentsList().stream().map(a -> new AttachmentDTO(a.getMimeType(), Base64.getEncoder().encodeToString(a.getData().toByteArray()))).toList()
        )).toList());
    }

    private Object mapValue(com.google.protobuf.Value v) {
        return switch (v.getKindCase()) {
            case STRING_VALUE -> v.getStringValue();
            case NUMBER_VALUE -> v.getNumberValue();
            case BOOL_VALUE -> v.getBoolValue();
            case STRUCT_VALUE ->
                    v.getStructValue().getFieldsMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> mapValue(e.getValue())));
            case LIST_VALUE -> v.getListValue().getValuesList().stream().map(this::mapValue).toList();
            default -> null;
        };
    }

    public record ResultDTO(java.util.List<StepReportDTO> reports) {
    }

    public record StepReportDTO(String status, String name, StepSummaryDTO summary,
                                java.util.List<AttachmentDTO> attachments) {
    }

    public record StepSummaryDTO(long totalDuration, Map<String, Object> metadata) {
    }

    public record AttachmentDTO(String mimeType, String data) {
    }
}
