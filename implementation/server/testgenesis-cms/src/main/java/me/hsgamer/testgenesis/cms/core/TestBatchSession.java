package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class TestBatchSession {
    private final String batchId;
    private final Long testId;
    private final String testName;
    private final boolean parallel;
    private final int totalIterations;
    private final List<TestSession> sessions = new ArrayList<>();
    private final Instant createdAt;
    
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

    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public synchronized void addSession(TestSession session) {
        sessions.add(session);
        session.addStatusConsumer(s -> notifyListeners());
    }
    
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
    
    public long getCompletedCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && me.hsgamer.testgenesis.cms.util.StatusUtil.isTerminal(s.getStatus().getState()))
            .count();
    }
}
