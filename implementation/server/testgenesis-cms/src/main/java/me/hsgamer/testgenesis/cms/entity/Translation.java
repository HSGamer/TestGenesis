package me.hsgamer.testgenesis.cms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "translation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Translation {
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
    private TranslationResult result;

    @PrePersist
    protected void onCreate() {
        requestedAt = Instant.now();
    }
}
