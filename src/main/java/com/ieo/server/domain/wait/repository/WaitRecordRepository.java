package com.ieo.server.domain.wait.repository;

import com.ieo.server.domain.wait.entity.WaitRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitRecordRepository extends JpaRepository<WaitRecord, Long> {

	Optional<WaitRecord> findByTripStopId(Long tripStopId);

	boolean existsByTripStopId(Long tripStopId);

	/** 운행의 상차·하차 대기 기록 */
	List<WaitRecord> findAllByTripStopTripId(Long tripId);
}
