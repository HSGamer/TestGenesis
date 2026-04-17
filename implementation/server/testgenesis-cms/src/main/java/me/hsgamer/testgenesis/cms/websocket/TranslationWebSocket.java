package me.hsgamer.testgenesis.cms.websocket;

import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import me.hsgamer.testgenesis.cms.core.TranslationSession;
import me.hsgamer.testgenesis.cms.service.TranslationManager;
import me.hsgamer.testgenesis.uap.v1.TranslationStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@WebSocket(path = "/telemetry/translation/{sessionId}")
public class TranslationWebSocket extends BaseWebSocket<TranslationSession> {
    @Inject
    TranslationManager translationManager;

    @Override
    protected Optional<TranslationSession> getSession(String id) {
        return translationManager.getTranslationSession(id);
    }

    @OnOpen
    public void onOpen(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onOpenBase(conn, id, s -> {
            Consumer<TranslationStatus> sc = status -> send(conn, WSMessage.StatusMsg.from(status));
            s.addStatusConsumer(sc);
            addCleanup(conn, () -> s.removeStatusConsumer(sc));

            Consumer<List<TranslationSession.GeneratedPayload>> rc = payloads -> send(conn, WSMessage.ResultMsg.from(payloads));
            s.addResultPayloadConsumer(rc);
            addCleanup(conn, () -> s.removeResultPayloadConsumer(rc));
        });
    }

    @OnClose
    public void onClose(WebSocketConnection conn, @PathParam("sessionId") String id) {
        onCloseBase(conn, id);
    }
}
