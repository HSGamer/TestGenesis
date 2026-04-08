package me.hsgamer.testgenesis.cms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "test_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private TestProject test;

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
}
