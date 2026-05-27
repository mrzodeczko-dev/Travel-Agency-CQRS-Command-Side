package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaDeadLetterRepository extends JpaRepository<DeadLetterEntity, UUID> {
}
