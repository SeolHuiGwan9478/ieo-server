package com.ieo.server.domain.plan.repository;

import com.ieo.server.domain.plan.entity.PlanCoupon;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCouponRepository extends JpaRepository<PlanCoupon, Long> {

	boolean existsByTripId(Long tripId);

	/** 최근 30일 동안 받은 쿠폰 수 (한도 검사) */
	long countByDriverUserIdAndIssuedAtGreaterThanEqual(Long driverId, Instant since);

	/** 화면에 보여 줄 쿠폰: 최근 받은 것 먼저 10장 */
	List<PlanCoupon> findTop10ByDriverUserIdOrderByIssuedAtDesc(Long driverId);
}
