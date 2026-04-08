package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.TestProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestProjectRepository extends JpaRepository<TestProject, Long> {
}
