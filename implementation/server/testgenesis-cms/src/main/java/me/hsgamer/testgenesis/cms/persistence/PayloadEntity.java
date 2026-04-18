package me.hsgamer.testgenesis.cms.persistence;

import com.google.protobuf.ByteString;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import me.hsgamer.testgenesis.cms.util.ProtoUtil;
import me.hsgamer.testgenesis.uap.v1.Attachment;
import me.hsgamer.testgenesis.uap.v1.Payload;

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

    @ManyToMany(mappedBy = "payloads")
    public java.util.List<TestEntity> tests = new java.util.ArrayList<>();

    @PreRemove
    void unlinkTests() {
        for (TestEntity test : tests) {
            test.getPayloads().remove(this);
        }
    }


    public Payload toProto() {
        Payload.Builder builder = Payload.newBuilder()
            .setType(type != null ? type : "")
            .setMetadata(ProtoUtil.jsonToStruct(metadata));

        if (attachmentData != null && attachmentData.length > 0) {
            builder.setAttachment(Attachment.newBuilder()
                .setName(attachmentName != null ? attachmentName : "attachment")
                .setMimeType(attachmentMimeType != null ? attachmentMimeType : "application/octet-stream")
                .setData(ByteString.copyFrom(attachmentData))
                .build());
        }

        return builder.build();
    }

    public void fillFromProto(Payload p) {
        this.type = p.getType();

        this.metadata = ProtoUtil.structToJson(p.getMetadata());
        if (p.hasAttachment()) {
            this.attachmentName = p.getAttachment().getName();
            this.attachmentMimeType = p.getAttachment().getMimeType();
            this.attachmentData = p.getAttachment().getData().toByteArray();
        } else if (this.attachmentData == null) {
            this.attachmentData = new byte[0];
        }

        this.name = "Translated: " + this.type;
        this.description = "Translated";
    }
}

