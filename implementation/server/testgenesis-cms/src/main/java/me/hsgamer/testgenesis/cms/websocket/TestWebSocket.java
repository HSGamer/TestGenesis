package me.hsgamer.testgenesis.cms.websocket;

import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import com.google.protobuf.util.Durations;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.UAPService;
import me.hsgamer.testgenesis.cms.util.ProtoUtil;
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
        return new ResultDTO(
                r.getReportsList().stream().map(report -> new StepReportDTO(
                        report.getStatus().name(), report.getName(),
                        new StepSummaryDTO(Durations.toMillis(report.getSummary().getTotalDuration()),
                                ProtoUtil.structToMap(report.getSummary().getMetadata()))
                )).toList(),
                r.getAttachmentsList().stream().map(a -> new AttachmentDTO(a.getMimeType(), Base64.getEncoder().encodeToString(a.getData().toByteArray()))).toList(),
                new ResultSummaryDTO(Durations.toMillis(r.getSummary().getTotalDuration()), ProtoUtil.structToMap(r.getSummary().getMetadata()))
        );
    }


    public record ResultDTO(java.util.List<StepReportDTO> reports, java.util.List<AttachmentDTO> attachments, ResultSummaryDTO summary) {
    }

    public record StepReportDTO(String status, String name, StepSummaryDTO summary) {
    }

    public record StepSummaryDTO(long totalDuration, java.util.Map<String, Object> metadata) {
    }

    public record ResultSummaryDTO(long totalDuration, java.util.Map<String, Object> metadata) {
    }

    public record AttachmentDTO(String mimeType, String data) {
    }
}
