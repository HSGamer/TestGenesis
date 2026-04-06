package me.hsgamer.testgenesis.cms.db.repository;

import io.helidon.data.Data;
import me.hsgamer.testgenesis.cms.db.entity.TestEntity;

@Data.Repository
public interface TestRepository extends Data.CrudRepository<TestEntity, Long> {
}
