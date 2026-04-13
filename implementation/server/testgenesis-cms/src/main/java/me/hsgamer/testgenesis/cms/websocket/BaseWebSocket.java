package me.hsgamer.testgenesis.cms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.Session;
import me.hsgamer.testgenesis.uap.v1.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public abstract class BaseWebSocket<S extends Session<?>> {
    private final Map<String, List<Runnable>> cleanupTasks = new ConcurrentHashMap<>();

    @Inject
    protected ObjectMapper mapper;

    protected abstract Optional<S> getSession(String id);

    protected void send(WebSocketConnection conn, Object msg) {
        try {
            conn.sendText(mapper.writeValueAsString(msg)).subscribe().with(v -> {
            }, e -> log.error("WS send failed", e));
        } catch (Exception e) {
            log.error("WS serialization failed", e);
        }
    }

    protected void onOpenBase(WebSocketConnection conn, String id, Consumer<S> extra) {
        S s = getSession(id).orElse(null);
        if (s == null) {
            send(conn, Map.of("type", "ERROR", "message", "Session not found"));
            conn.close();
            return;
        }

        Consumer<Telemetry> tc = t -> send(conn, WSMessage.TelemetryMsg.from(t));
        s.addTelemetryConsumer(tc);
        addCleanup(conn, () -> s.removeTelemetryConsumer(tc));

        extra.accept(s);
    }

    protected void onCloseBase(WebSocketConnection conn, String id) {
        Optional.ofNullable(cleanupTasks.remove(conn.id())).ifPresent(tasks -> tasks.forEach(Runnable::run));
        log.info("Connection {} closed.", conn.id());
    }

    protected void addCleanup(WebSocketConnection conn, Runnable task) {
        cleanupTasks.computeIfAbsent(conn.id(), k -> new ArrayList<>()).add(task);
    }
}
