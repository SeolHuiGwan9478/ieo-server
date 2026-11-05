package com.ieo.server.domain.broker.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * 내 회사 리포트 (BRK-042). 기간 안에 등록한 오더와 배차된 운행 기준.
 * 운행 수는 배차된 시점으로 세므로, 기간 안에 배차돼 아직 진행 중인 운행도 dispatchedCount에 든다
 */
public record CompanyReportResponse(
		LocalDate from,
		LocalDate to,
		long orderCount,
		int dispatchedCount,
		int completedCount,
		int canceledCount,
		int canceledByBrokerCount,
		int canceledByDriverCount,
		Integer avgConfirmMinutes,
		long waitFeeTotal,
		List<VehicleRow> vehicles) {

	/**
	 * 차량별 이행률 = 완료 / (완료 + 취소). 끝난 운행이 없으면 null.
	 * 취소는 누가 했든 센다 (주선사가 취소한 것도 그 차량의 배차가 깨진 것이라서)
	 */
	public record VehicleRow(
			String plateNumber,
			String driverName,
			int dispatched,
			int completed,
			int canceled,
			Integer completionPercent) {
	}
}
