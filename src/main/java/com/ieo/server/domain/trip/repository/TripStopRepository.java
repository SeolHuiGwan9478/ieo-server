package com.ieo.server.domain.trip.repository;

import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopRepository extends JpaRepository<TripStop, Long> {

	Optional<TripStop> findByTripIdAndType(Long tripId, StopType type);
}
