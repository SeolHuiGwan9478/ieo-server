package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.OrderReport;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReportRepository extends JpaRepository<OrderReport, Long> {

	boolean existsByOrderIdAndReporterUserId(Long orderId, Long userId);

	long countByOrderId(Long orderId);

	/** 이 오더들 중 차주가 신고한 오더 id */
	@Query("select r.order.id from OrderReport r where r.reporter.userId = :userId and r.order.id in :orderIds")
	Set<Long> findReportedOrderIds(@Param("userId") Long userId, @Param("orderIds") Collection<Long> orderIds);
}
