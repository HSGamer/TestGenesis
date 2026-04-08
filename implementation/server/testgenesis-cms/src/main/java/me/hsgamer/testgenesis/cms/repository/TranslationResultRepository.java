package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.TranslationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationResultRepository extends JpaRepository<TranslationResult, Long> {
}
