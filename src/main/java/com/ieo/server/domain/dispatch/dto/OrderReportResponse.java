package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.entity.OrderReport;
import com.ieo.server.domain.dispatch.enums.ReportType;
import java.time.Instant;

public record OrderReportResponse(Long id, Long orderId, ReportType type, String typeLabel, String memo, Instant createdAt) {

	public static OrderReportResponse from(OrderReport report) {
		return new OrderReportResponse(report.getId(), report.getOrder().getId(), report.getType(),
				report.getType().getLabel(), report.getMemo(), report.getCreatedAt());
	}
}
