package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaHotelRepository extends JpaRepository<HotelEntity, Long> {

}
