package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.TranslationEntity;

@Data.Repository
public interface TranslationRepository extends Data.CrudRepository<TranslationEntity, Long> {
}
