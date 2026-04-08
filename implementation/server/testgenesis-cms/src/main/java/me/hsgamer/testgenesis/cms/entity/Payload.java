package me.hsgamer.testgenesis.cms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payload")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payload {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "payload_type")
    private String payloadType;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @OneToMany(mappedBy = "payload", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();
}
