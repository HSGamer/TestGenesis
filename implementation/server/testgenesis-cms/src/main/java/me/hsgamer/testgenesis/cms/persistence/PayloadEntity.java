package me.hsgamer.testgenesis.cms.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class PayloadEntity extends PanacheEntity {
    public String name;
    public String description;
    public String type;

    public String attachmentName;
    public String attachmentMimeType;

    @Lob
    @Column(length = 10485760) // 10MB limit for now
    public byte[] attachmentData;

    @Lob
    @Column(columnDefinition = "TEXT")
    public String metadata;
}
