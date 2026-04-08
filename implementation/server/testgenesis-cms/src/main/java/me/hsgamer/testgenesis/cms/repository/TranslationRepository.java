package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {
}
