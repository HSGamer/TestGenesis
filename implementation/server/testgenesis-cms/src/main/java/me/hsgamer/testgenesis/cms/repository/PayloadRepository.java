package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.Payload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadRepository extends JpaRepository<Payload, Long> {
}
