package com.ieo.server.domain.trip.repository;

import com.ieo.server.domain.trip.entity.TripPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPositionRepository extends JpaRepository<TripPosition, Long> {
}
