package com.ieo.server.domain.broker.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * 함께 일한 차주 리포트 (BRK-043). 기간 안에 우리 회사 오더로 배차된 차주마다 이행 기록.
 * 단골(linked)이 아니어도 우리 일을 했으면 나온다. 전화번호는 단골일 때만 (2-4 규칙)
 */
public record DriverReportResponse(
		LocalDate from,
		LocalDate to,
		List<DriverRow> drivers) {

	/**
	 * 정시 도착 = 상차지 예정 시각 + 30분 안에 진입. 상차지 진입 기록이 있는 완료 운행만 센다 (onTimeBase).
	 * canceledCount·lateCancelCount는 차주가 취소한 것만 (늦은 취소 = 상차 예정 2시간 이내)
	 */
	public record DriverRow(
			Long driverUserId,
			String name,
			String phone,
			boolean linked,
			int dispatched,
			int completed,
			int canceledCount,
			int lateCancelCount,
			int onTimeCount,
			int onTimeBase,
			Integer onTimePercent) {
	}
}
