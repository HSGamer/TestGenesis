package me.hsgamer.testgenesis.cms.db.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Test case.
 */
@Entity
@Table(name = "test")
public class TestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String name;

    @Column(name = "test_type")
    private String testType;

    @ManyToMany
    @JoinTable(
            name = "test_payload",
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "payload_id")
    )
    private List<PayloadEntity> payloads = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public List<PayloadEntity> getPayloads() {
        return payloads;
    }

    public void setPayloads(List<PayloadEntity> payloads) {
        this.payloads = payloads;
    }

    public int getPayloadCount() {
        return payloads != null ? payloads.size() : 0;
    }
}
