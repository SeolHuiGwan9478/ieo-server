package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.Cancellation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

	Optional<Cancellation> findByTripId(Long tripId);

	/** 이 사람이 취소한 기록. 차주 이행 기록(늦은 취소 수)에 쓴다 */
	List<Cancellation> findAllByCanceledById(Long userId);

	/** 리포트: 여러 운행의 취소 기록 */
	List<Cancellation> findAllByTripIdIn(Collection<Long> tripIds);
}
