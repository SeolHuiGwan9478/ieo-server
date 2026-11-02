package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.OrderReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReportRepository extends JpaRepository<OrderReport, Long> {

	boolean existsByOrderIdAndReporterUserId(Long orderId, Long userId);

	long countByOrderId(Long orderId);
}
