package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, Long> {
}

