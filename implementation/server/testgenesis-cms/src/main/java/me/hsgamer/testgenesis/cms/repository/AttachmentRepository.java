package me.hsgamer.testgenesis.cms.repository;

import me.hsgamer.testgenesis.cms.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
