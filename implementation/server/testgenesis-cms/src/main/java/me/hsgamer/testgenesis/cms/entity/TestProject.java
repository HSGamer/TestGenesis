package me.hsgamer.testgenesis.cms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestProject {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String name;

    @Column(name = "test_type")
    private String testType;

    @ManyToMany
    @JoinTable(
            name = "test_payload_map",
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "payload_id")
    )
    private List<Payload> payloads = new ArrayList<>();

    public int getPayloadCount() {
        return payloads != null ? payloads.size() : 0;
    }
}
