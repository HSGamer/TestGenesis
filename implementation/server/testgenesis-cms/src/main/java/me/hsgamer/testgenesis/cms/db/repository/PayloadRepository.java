package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.PayloadEntity;

@Data.Repository
public interface PayloadRepository extends Data.CrudRepository<PayloadEntity, Long> {
}
