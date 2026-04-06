package me.hsgamer.testgenesis.cms.db.entity;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "test_result")
public class TestResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private TestEntity test;

    @Column(name = "agent_display_name")
    private String agentDisplayName;

    private String status;

    @Column(name = "total_duration")
    private Duration totalDuration;

    @Column(name = "start_time")
    private Instant startTime;

    @Lob
    @Column(name = "summary_json")
    private String summaryJson;

    @Lob
    @Column(name = "telemetry_log")
    private String telemetryLog;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        registeredAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TestEntity getTest() {
        return test;
    }

    public void setTest(TestEntity test) {
        this.test = test;
    }

    public String getAgentDisplayName() {
        return agentDisplayName;
    }

    public void setAgentDisplayName(String agentDisplayName) {
        this.agentDisplayName = agentDisplayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Duration totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getTelemetryLog() {
        return telemetryLog;
    }

    public void setTelemetryLog(String telemetryLog) {
        this.telemetryLog = telemetryLog;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
