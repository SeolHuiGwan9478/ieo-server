package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchRequestRepository extends JpaRepository<DispatchRequest, Long> {

	/** 오더에 들어온 요청. 먼저 요청한 순서 */
	List<DispatchRequest> findAllByOrderIdOrderByCreatedAtAsc(Long orderId);

	Optional<DispatchRequest> findByOrderIdAndDriverUserId(Long orderId, Long userId);

	/** 내 요청. 최근 것 먼저 */
	List<DispatchRequest> findAllByDriverUserIdOrderByCreatedAtDesc(Long userId);

	/** 차주가 걸어 둔 대기 중 요청 수 (DSP-024). since = 지금 − 10분: 그 전 요청은 만료 */
	long countByDriverUserIdAndStatusAndCreatedAtAfter(Long userId, DispatchRequestStatus status, Instant since);
}
