package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findAllByOrderByCreatedAtAsc(Pageable pageable);
    List<OutboxEntity> findAllByTypeInOrderByCreatedAtAsc(List<String> types, Pageable pageable);
}
