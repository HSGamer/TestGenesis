package me.hsgamer.testgenesis.cms.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "translation_result")
public class TranslationResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translation_id")
    private TranslationEntity translation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_payload_id")
    private PayloadEntity resultPayload;

    @Column(name = "agent_display_name")
    private String agentDisplayName;

    private String status;

    @Lob
    @Column(name = "telemetry_log")
    private String telemetryLog;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TranslationEntity getTranslation() {
        return translation;
    }

    public void setTranslation(TranslationEntity translation) {
        this.translation = translation;
    }

    public PayloadEntity getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(PayloadEntity resultPayload) {
        this.resultPayload = resultPayload;
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

    public String getTelemetryLog() {
        return telemetryLog;
    }

    public void setTelemetryLog(String telemetryLog) {
        this.telemetryLog = telemetryLog;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
