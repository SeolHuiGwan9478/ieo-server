package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

	/** 주선사의 오더. 최근 등록한 순서 */
	Page<Order> findAllByBrokerIdOrderByCreatedAtDesc(Long brokerId, Pageable pageable);

	Page<Order> findAllByBrokerIdAndStatusOrderByCreatedAtDesc(Long brokerId, OrderStatus status, Pageable pageable);

	/**
	 * 차주가 볼 수 있는 모집 중 오더 (DSP-010). 지정 배차 오더는 그 차주에게만 보인다.
	 * 상차가 빠른 순서. 거리·방향 필터는 서비스가 상하차지 좌표로 거른다
	 */
	@Query("""
			select o from Order o
			where o.status = com.ieo.server.domain.dispatch.enums.OrderStatus.OPEN
			  and o.pickupUntil > :now
			  and (o.targetDriver is null or o.targetDriver.userId = :driverId)
			order by o.pickupFrom asc
			""")
	List<Order> findOpenFor(@Param("driverId") Long driverId, @Param("now") Instant now, Pageable pageable);

	/** 리포트: 기간 안에 등록한 오더 수 (BRK-042) */
	long countByBrokerIdAndCreatedAtBetween(Long brokerId, Instant from, Instant to);
}
