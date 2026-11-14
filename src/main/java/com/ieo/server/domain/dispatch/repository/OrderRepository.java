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

	/** 자동 마감: 상차 가능 시간이 끝났는데 아직 모집 중인 오더 (OrderExpirer). idx_orders_status_pickup을 탄다 */
	List<Order> findAllByStatusAndPickupUntilLessThanEqual(OrderStatus status, Instant now);

	/** 이 회사가 이 차주에게 지정한, 아직 모집 중인 오더 (단골 해제 때 마감) */
	List<Order> findAllByBrokerIdAndTargetDriverUserIdAndStatus(Long brokerId, Long driverUserId, OrderStatus status);

	/** 리포트: 기간 안에 등록한 오더 수 (BRK-042) */
	long countByBrokerIdAndCreatedAtBetween(Long brokerId, Instant from, Instant to);

	/** 대시보드: 기간 안에 등록한 오더 (등록 날짜별로 센다) */
	List<Order> findAllByBrokerIdAndCreatedAtBetween(Long brokerId, Instant from, Instant to);

	/**
	 * 재위탁 의심 (DSP-041): 다른 주선사가 올린 모집 중 오더 중 상차지·하차지가 각각 [meters] 안이고 상차 시간대가 겹치는 것.
	 * 가장 먼저 올라온 것 하나
	 */
	@Query(nativeQuery = true, value = """
			select o.id from orders o
			join sites p on p.id = o.pickup_site_id
			join sites d on d.id = o.dropoff_site_id
			where o.status = 'OPEN'
			  and o.broker_id <> :brokerId
			  and o.id <> :orderId
			  and o.pickup_from < :pickupUntil
			  and o.pickup_until > :pickupFrom
			  and ST_DWithin(p.location, (select location from sites where id = :pickupSiteId), :meters)
			  and ST_DWithin(d.location, (select location from sites where id = :dropoffSiteId), :meters)
			order by o.created_at asc
			limit 1
			""")
	java.util.Optional<Long> findReconsignmentSource(@Param("orderId") Long orderId, @Param("brokerId") Long brokerId,
			@Param("pickupSiteId") Long pickupSiteId, @Param("dropoffSiteId") Long dropoffSiteId,
			@Param("pickupFrom") Instant pickupFrom, @Param("pickupUntil") Instant pickupUntil,
			@Param("meters") double meters);

	/** 운영자 화면: 재위탁 의심 표시가 붙은 오더, 최근 것부터 */
	List<Order> findAllByReconsignmentSuspectOfIsNotNullOrderByCreatedAtDesc(Pageable pageable);
}
