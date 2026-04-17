package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.hsgamer.testgenesis.cms.util.StatusUtil.isTerminal;

@Getter
public class TestBatchSession {
    private final String batchId;
    private final Long testId;
    private final String testName;
    private final boolean parallel;
    private final int totalIterations;
    private final List<TestSession> sessions = new ArrayList<>();
    private final Instant createdAt;
    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    @Setter
    private BatchStatus status = BatchStatus.PENDING;

    public TestBatchSession(Long testId, String testName, boolean parallel, int totalIterations) {
        this.batchId = "BCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.testId = testId;
        this.testName = testName;
        this.parallel = parallel;
        this.totalIterations = totalIterations;
        this.createdAt = Instant.now();
    }

    public synchronized void addSession(TestSession session) {
        sessions.add(session);
        session.addStatusConsumer(s -> notifyListeners());
    }

    public synchronized void markSessionAccepted(TestSession session, Runnable onCompletion) {
        addSession(session);
        session.onCompletion(() -> {
            if (onCompletion != null) onCompletion.run();
            checkCompletion();
        });
    }

    public synchronized void markSessionFailed() {
        if (status != BatchStatus.CANCELLED) {
            status = BatchStatus.FAILED;
            notifyListeners();
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private synchronized void checkCompletion() {
        if (getCompletedCount() >= totalIterations && status == BatchStatus.RUNNING) {
            status = BatchStatus.COMPLETED;
            notifyListeners();
        }
    }

    public long getCompletedCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && isTerminal(s.getStatus().getState()))
            .count();
    }
}
