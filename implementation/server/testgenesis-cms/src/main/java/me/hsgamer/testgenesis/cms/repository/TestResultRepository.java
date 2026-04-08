package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
}
