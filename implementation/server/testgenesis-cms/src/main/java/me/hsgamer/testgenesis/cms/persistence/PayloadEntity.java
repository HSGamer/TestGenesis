package me.hsgamer.testgenesis.cms.persistence;

import com.google.protobuf.ByteString;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
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

    public Payload toProto() {
        return Payload.newBuilder()
                .setType(type)
                .setMetadata(ProtoUtil.jsonToStruct(metadata))
                .setAttachment(Attachment.newBuilder()
                        .setName(attachmentName)
                        .setMimeType(attachmentMimeType)
                        .setData(ByteString.copyFrom(attachmentData))
                        .build())
                .build();
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

