package me.hsgamer.testgenesis.cms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "translation_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translation_id")
    private Translation translation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_payload_id")
    private Payload resultPayload;

    @Column(name = "agent_display_name")
    private String agentDisplayName;

    private String status;

    @Lob
    @Column(name = "telemetry_log")
    private String telemetryLog;

    @Column(name = "completed_at")
    private Instant completedAt;
}
