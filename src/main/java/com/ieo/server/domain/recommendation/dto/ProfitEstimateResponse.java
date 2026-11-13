package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.recommendation.service.ProfitEstimator.Estimate;

/**
 * 오더 한 건의 실수익 내역 (DSP-011).
 *
 * @param netProfit   운임 − 기름값 − 통행료
 * @param fuelCost    공차 + 운송 구간 기름값(원)
 * @param tollFare    공차 + 운송 구간 예상 통행료(원). 경로를 받지 못했으면 null
 * @param emptyMeters 출발점 → 상차지 거리(m)
 * @param originName  상하차지에 있어 그곳에서 잰 경우 그 이름. 지금 위치에서 쟀으면 null
 */
public record ProfitEstimateResponse(int netProfit, int fuelCost, Integer tollFare, long emptyMeters, String originName) {

	public static ProfitEstimateResponse from(Estimate estimate, String originName) {
		return new ProfitEstimateResponse(estimate.netProfit(), estimate.fuelCost(), estimate.tollFare(), estimate.emptyMeters(),
				originName);
	}
}
