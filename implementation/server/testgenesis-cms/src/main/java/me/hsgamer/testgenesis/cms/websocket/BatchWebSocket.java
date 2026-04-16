package me.hsgamer.testgenesis.cms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.TestBatchSession;
import me.hsgamer.testgenesis.cms.service.UAPService;

import java.util.Optional;
import java.util.Map;

@WebSocket(path = "/telemetry/test/batch/{batchId}")
@Slf4j
public class BatchWebSocket {
    @Inject
    UAPService uapService;

    @Inject
    ObjectMapper mapper;

    @OnOpen
    public void onOpen(WebSocketConnection conn, @PathParam("batchId") String id) {
        TestBatchSession b = uapService.getBatchSession(id).orElse(null);
        if (b == null) {
            send(conn, Map.of("type", "ERROR", "message", "Batch not found"));
            conn.close();
            return;
        }

        Runnable updater = () -> send(conn, WSMessage.BatchUpdateMsg.from(b));
        updater.run();
        b.addListener(updater);
    }

    private void send(WebSocketConnection conn, Object msg) {
        try {
            conn.sendText(mapper.writeValueAsString(msg)).subscribe().with(v -> {}, e -> log.error("Batch WS send failed", e));
        } catch (Exception e) {
            log.error("Batch WS serialization failed", e);
        }
    }
}
