package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * Service for tracking system-wide performance statistics
 */
@ApplicationScoped
public class StatisticsService {
    private final AtomicLong completedSessions = new AtomicLong(0);
    private final LongAccumulator totalNegotiationTime = new LongAccumulator(Long::sum, 0);
    private final AtomicLong negotiationCount = new AtomicLong(0);
    private final long startTime = System.currentTimeMillis();

    /**
     * Reports a completed session negotiation duration.
     * @param durationMs The duration in milliseconds from SessionProposal to SessionReady.
     */
    public void reportNegotiation(long durationMs) {
        totalNegotiationTime.accumulate(durationMs);
        negotiationCount.incrementAndGet();
    }

    /**
     * Reports that a test session has completed.
     */
    public void reportSessionCompletion() {
        completedSessions.incrementAndGet();
    }

    /**
     * Returns the average negotiation time in milliseconds.
     * @return Average negotiation time.
     */
    public double getAvgNegotiationTime() {
        long count = negotiationCount.get();
        return count == 0 ? 0 : (double) totalNegotiationTime.get() / count;
    }

    /**
     * Returns the throughput in sessions per minute.
     * @return Sessions per minute.
     */
    public double getThroughputPerMinute() {
        long durationMs = System.currentTimeMillis() - startTime;
        if (durationMs < 1000) return 0; // Avoid high spikes at startup
        return (double) completedSessions.get() / (durationMs / 60000.0);
    }

    /**
     * Returns the total number of completed sessions.
     * @return Total completed sessions.
     */
    public long getCompletedSessionsCount() {
        return completedSessions.get();
    }
}
