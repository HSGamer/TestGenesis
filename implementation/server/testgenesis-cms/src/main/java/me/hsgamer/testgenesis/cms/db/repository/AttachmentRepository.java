package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.AttachmentEntity;

@Data.Repository
public interface AttachmentRepository extends Data.CrudRepository<AttachmentEntity, Long> {
}
