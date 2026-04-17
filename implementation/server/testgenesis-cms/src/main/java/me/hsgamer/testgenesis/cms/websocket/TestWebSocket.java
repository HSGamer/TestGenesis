package me.hsgamer.testgenesis.cms.websocket;

import com.google.protobuf.util.Durations;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.TestSession;
import me.hsgamer.testgenesis.cms.service.TestSessionManager;
import me.hsgamer.testgenesis.cms.util.ProtoUtil;
import me.hsgamer.testgenesis.uap.v1.TestResult;
import me.hsgamer.testgenesis.uap.v1.TestStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@WebSocket(path = "/telemetry/test/{sessionId}")
public class TestWebSocket extends BaseWebSocket<TestSession> {
    @Inject
    TestSessionManager testSessionManager;

    @Override
    protected Optional<TestSession> getSession(String id) {
        return testSessionManager.getTestSession(id);
    }

    @OnOpen
    public void onOpen(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onOpenBase(conn, id, s -> {
            Consumer<TestStatus> sc = status -> send(conn, WSMessage.StatusMsg.from(status));
            s.addStatusConsumer(sc);
            addCleanup(conn, () -> s.removeStatusConsumer(sc));

            Consumer<TestResult> rc = result -> send(conn, WSMessage.ResultMsg.from(mapResult(s, result)));
            s.addResultConsumer(rc);
            addCleanup(conn, () -> s.removeResultConsumer(rc));
        });
    }

    @OnClose
    public void onClose(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onCloseBase(conn, id);
    }

    private ResultDTO mapResult(TestSession session, TestResult r) {
        AtomicInteger ai = new AtomicInteger(0);
        return new ResultDTO(
            r.getReportsList().stream().map(report -> new StepReportDTO(
                report.getStatus().name(), report.getName(),
                new StepSummaryDTO(Durations.toMillis(report.getSummary().getTotalDuration()),
                    ProtoUtil.structToMap(report.getSummary().getMetadata()))
            )).toList(),
            r.getAttachmentsList().stream().map(a -> {
                int idx = ai.getAndIncrement();
                return new AttachmentDTO(a.getMimeType(), a.getName(), "/tests/" + session.getSessionId() + "/attachments/" + idx);
            }).toList(),
            new ResultSummaryDTO(Durations.toMillis(r.getSummary().getTotalDuration()), ProtoUtil.structToMap(r.getSummary().getMetadata()))
        );
    }


    public record ResultDTO(List<StepReportDTO> reports, List<AttachmentDTO> attachments, ResultSummaryDTO summary) {
    }

    public record StepReportDTO(String status, String name, StepSummaryDTO summary) {
    }

    public record StepSummaryDTO(long totalDuration, Map<String, Object> metadata) {
    }

    public record ResultSummaryDTO(long totalDuration, Map<String, Object> metadata) {
    }

    public record AttachmentDTO(String mimeType, String name, String url) {
    }
}
