package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.dispatch.dto.OrderResponse;

/**
 * 추천 하나. order.distanceM은 세운 자리에서 상차지까지 공차 거리(emptyMeters와 같다).
 *
 * @param emptyMeters 세운 자리 → 상차지 공차 거리
 * @param fuelCost    공차 + 운송 구간 기름값(원)
 * @param netProfit   실수익 = 운임 − 기름값 − 통행료 (REC-010)
 * @param longWait    상차지 평균 대기가 길었던 곳 (REC-012)
 * @param tollFare    공차 + 운송 구간 예상 통행료(원). 경로를 받지 못했으면 null
 */
public record RecommendationResponse(OrderResponse order, long emptyMeters, int fuelCost, int netProfit, boolean longWait, Integer tollFare) {
}
