package com.ieo.server.domain.broker.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * 상하차지별 리포트 (BRK-041). 우리 회사 운행이 들른 상하차지마다, 그곳의 모든 회사 대기 기록으로 평균을 낸다.
 * 화주 견적·대기비 청구 근거로 쓴다. recordCount가 minSamples 미만인 곳은 뺀다.
 * 요일·시간대별 대기는 기록이 쌓이면 붙인다 (원작자 SiteStats TODO와 같이)
 */
public record SiteReportResponse(
		LocalDate from,
		LocalDate to,
		int minSamples,
		List<SiteRow> sites) {

	/** 대기 = 진입~작업 시작, 작업 = 작업 시작~이탈 (분). 기록이 많은 순 */
	public record SiteRow(
			Long siteId,
			String name,
			String address,
			int recordCount,
			int avgWaitMinutes,
			int maxWaitMinutes,
			Integer avgWorkMinutes) {
	}
}
