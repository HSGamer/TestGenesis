package me.hsgamer.testgenesis.cms.core;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.hsgamer.testgenesis.uap.v1.TestState;

import static me.hsgamer.testgenesis.cms.util.StatusUtil.isTerminal;

@Getter
public class TestBatchSession {
    private final String batchId;
    private final String testName;
    private final TestTicket ticket;
    private final int totalIterations;
    private final List<TestSession> sessions = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.Queue<String> agentQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger failedRegistrations = new AtomicInteger(0);
    private final Instant createdAt;

    @Setter
    private volatile BatchStatus status = BatchStatus.PENDING;

    public TestBatchSession(String testName, TestTicket ticket, int totalIterations) {
        this.batchId = "BCH-" + UUID.randomUUID();
        this.testName = testName;
        this.ticket = ticket;
        this.totalIterations = totalIterations;
        this.createdAt = Instant.now();
    }

    public void addAgent(String agentId) {
        agentQueue.add(agentId);
    }

    public String pollAgent() {
        return agentQueue.poll();
    }

    public boolean isQueueEmpty() {
        return agentQueue.isEmpty();
    }

    public synchronized void addSession(TestSession session) {
        sessions.add(session);
        session.onCompletion(this::checkCompletion);
    }

    public void markRegistrationFailed() {
        failedRegistrations.incrementAndGet();
        notifyListeners();
        checkCompletion();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private synchronized void checkCompletion() {
        if (status != BatchStatus.RUNNING) return;

        long terminalCount = sessions.stream()
            .filter(s -> s.getStatus() != null && isTerminal(s.getStatus().getState()))
            .count();

        if (terminalCount + failedRegistrations.get() >= totalIterations) {
            status = BatchStatus.COMPLETED;
        }

        // Always notify on completion/terminal state to trigger manager
        notifyListeners();
    }

    public long getCompletedCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && isTerminal(s.getStatus().getState()))
            .count() + failedRegistrations.get();
    }

    public long getPassedCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && s.getStatus().getState() == TestState.TEST_STATE_COMPLETED)
            .count();
    }

    public long getFailedCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && s.getStatus().getState() == TestState.TEST_STATE_FAILED)
            .count() + failedRegistrations.get();
    }

    public long getRunningCount() {
        return sessions.stream()
            .filter(s -> s.getStatus() != null && s.getStatus().getState() == TestState.TEST_STATE_RUNNING)
            .count();
    }

    public long getPendingCount() {
        return totalIterations - getCompletedCount() - getRunningCount();
    }
}
