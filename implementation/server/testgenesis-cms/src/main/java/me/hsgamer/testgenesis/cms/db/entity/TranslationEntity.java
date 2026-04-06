package me.hsgamer.testgenesis.cms.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "translation")
public class TranslationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "source_test_id")
    private Long sourceTestId;

    @Column(name = "target_format")
    private String targetFormat;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @OneToOne(mappedBy = "translation", cascade = CascadeType.ALL, orphanRemoval = true)
    private TranslationResultEntity result;

    @PrePersist
    protected void onCreate() {
        requestedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceTestId() {
        return sourceTestId;
    }

    public void setSourceTestId(Long sourceTestId) {
        this.sourceTestId = sourceTestId;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public TranslationResultEntity getResult() {
        return result;
    }

    public void setResult(TranslationResultEntity result) {
        this.result = result;
    }
}
