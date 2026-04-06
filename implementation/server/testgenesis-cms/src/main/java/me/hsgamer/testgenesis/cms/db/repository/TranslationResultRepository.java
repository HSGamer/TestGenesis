package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.TranslationResultEntity;

@Data.Repository
public interface TranslationResultRepository extends Data.CrudRepository<TranslationResultEntity, Long> {
}
