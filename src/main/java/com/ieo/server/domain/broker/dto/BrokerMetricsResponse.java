package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerMetrics;
import java.time.Instant;

/**
 * 주선사 신뢰 지표 (BRK-030~032). 차주 오더 카드와 주선사 콘솔에 같은 모양으로 준다.
 * published가 false면(최근 180일 끝난 운행이 20건 미만) 지표 값은 모두 null이고, 화면은 "기록 쌓는 중"을 보여 준다.
 * published여도 해당 건이 없는 지표는 null이다 (예: 인수증 결제가 한 건도 없으면 avgPaymentDays).
 * 회사 전체 숫자만 준다. 차주별로 쪼개 주지 않는다 (누가 "못 받음"으로 남겼는지 드러나지 않게)
 */
public record BrokerMetricsResponse(
		boolean published,
		int sampleCount,
		int minSampleCount,
		Integer avgPaymentDays,
		Integer latePaymentPercent,
		Integer waitFeePaidPercent,
		Integer avgWaitMinutes,
		Instant calculatedAt) {

	/** 아직 끝난 운행이 없어 계산한 적 없는 회사 */
	public static BrokerMetricsResponse empty() {
		return new BrokerMetricsResponse(false, 0, BrokerMetrics.MIN_COMPLETED_TRIPS, null, null, null, null, null);
	}

	public static BrokerMetricsResponse from(BrokerMetrics metrics) {
		if (metrics == null) {
			return empty();
		}
		boolean published = metrics.isPublished();
		return new BrokerMetricsResponse(
				published,
				metrics.getSampleCount(),
				BrokerMetrics.MIN_COMPLETED_TRIPS,
				published ? metrics.getAvgPaymentDays() : null,
				published ? metrics.getLatePaymentPercent() : null,
				published ? metrics.getWaitFeePaidPercent() : null,
				published ? metrics.getAvgWaitMinutes() : null,
				metrics.getCalculatedAt());
	}
}
