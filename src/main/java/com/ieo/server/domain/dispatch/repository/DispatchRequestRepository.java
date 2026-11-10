package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispatchRequestRepository extends JpaRepository<DispatchRequest, Long> {

	/** 오더에 들어온 요청. 먼저 요청한 순서 */
	List<DispatchRequest> findAllByOrderIdOrderByCreatedAtAsc(Long orderId);

	/** 이 오더에 낸 내 요청 중 가장 최근 것. 철회 뒤 다시 요청하면 여러 줄이 있다 */
	Optional<DispatchRequest> findFirstByOrderIdAndDriverUserIdOrderByIdDesc(Long orderId, Long userId);

	List<DispatchRequest> findAllByOrderIdAndDriverUserId(Long orderId, Long userId);

	/** 내 요청. 최근 것 먼저 */
	List<DispatchRequest> findAllByDriverUserIdOrderByCreatedAtDesc(Long userId);

	/** 차주가 걸어 둔 대기 중 요청 수 (DSP-024). since = 지금 − 10분: 그 전 요청은 만료 */
	long countByDriverUserIdAndStatusAndCreatedAtAfter(Long userId, DispatchRequestStatus status, Instant since);

	/** 만료됐는데 상태가 아직 PENDING인 내 요청 (until = 지금 − 10분). 놓친 만료 알림을 채울 때 */
	List<DispatchRequest> findAllByDriverUserIdAndStatusAndCreatedAtLessThanEqual(
			Long userId, DispatchRequestStatus status, Instant until);

	/** 오더 목록에 내 요청 상태를 붙일 때 */
	List<DispatchRequest> findAllByDriverUserIdAndOrderIdIn(Long userId, Collection<Long> orderIds);

	/** 오더별 대기 중(만료 전) 요청 수. 주선사 오더 목록에 붙인다 */
	@Query("""
			select r.order.id as orderId, count(r) as count from DispatchRequest r
			where r.order.id in :orderIds
			  and r.status = com.ieo.server.domain.dispatch.enums.DispatchRequestStatus.PENDING
			  and r.createdAt > :since
			group by r.order.id
			""")
	List<OrderCount> countPendingByOrderIds(@Param("orderIds") Collection<Long> orderIds, @Param("since") Instant since);

	interface OrderCount {
		Long getOrderId();

		long getCount();
	}

	/** 짧은 시간에 요청을 반복하는지 (DSP-025). 상태는 따지지 않는다 */
	long countByDriverUserIdAndCreatedAtAfter(Long userId, java.time.Instant after);
}
