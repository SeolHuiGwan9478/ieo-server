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

	/**
	 * 상차지가 (lat, lng)에서 meters 안인 모집 중 오더, 가까운 순 (REC-003).
	 * 지정 배차 오더는 그 차주에게만. geography라서 거리는 지구 타원체 위의 미터다
	 */
	@Query(nativeQuery = true, value = """
			select o.* from orders o
			join sites s on s.id = o.pickup_site_id
			where o.status = 'OPEN'
			  and o.pickup_until > :now
			  and (o.target_driver_id is null or o.target_driver_id = :driverId)
			  and ST_DWithin(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :meters)
			order by ST_Distance(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
			limit :limit
			""")
	List<Order> findOpenNear(@Param("driverId") Long driverId, @Param("now") Instant now,
			@Param("lat") double lat, @Param("lng") double lng, @Param("meters") double meters,
			@Param("limit") int limit);

	/** 리포트: 기간 안에 등록한 오더 수 (BRK-042) */
	long countByBrokerIdAndCreatedAtBetween(Long brokerId, Instant from, Instant to);
}
