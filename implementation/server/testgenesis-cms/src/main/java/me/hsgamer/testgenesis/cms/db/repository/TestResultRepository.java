package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.TestResultEntity;

@Data.Repository
public interface TestResultRepository extends Data.CrudRepository<TestResultEntity, Long> {
}
