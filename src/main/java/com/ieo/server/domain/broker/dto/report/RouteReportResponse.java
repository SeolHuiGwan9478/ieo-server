package com.ieo.server.domain.broker.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * 노선별 리포트 (BRK-040). 모든 회사의 Ieo 배차 완료 운행을 상하차지 시·군·구로 묶는다.
 * 어느 회사·차주의 운행인지는 나오지 않는다 (BRK-044). tripCount가 minSamples 미만인 노선은 뺀다
 */
public record RouteReportResponse(
		LocalDate from,
		LocalDate to,
		int minSamples,
		List<Route> routes) {

	/** 운임은 원, 소요 시간은 출발~하차 완료(분). 운행이 많은 순 */
	public record Route(
			String pickupArea,
			String dropoffArea,
			int tripCount,
			int avgFare,
			int medianFare,
			int minFare,
			int maxFare,
			Integer avgDurationMinutes,
			List<Month> months) {
	}

	/** 월별 추이 (한국 날짜 기준, 오래된 달부터) */
	public record Month(String month, int tripCount, int avgFare) {
	}
}
