package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.dispatch.dto.OrderResponse;

/**
 * 추천 후보 하나. order.distanceM은 하차지에서 상차지까지 공차 거리(emptyMeters와 같다).
 *
 * @param emptyMeters 하차지 → 다음 상차지 공차 거리(어림)
 * @param fuelCost    공차 연료비(원)
 * @param netProfit   실수익 = 운임 − 공차 연료비 − 통행료(지금은 0) (REC-010)
 * @param longWait    상차지 평균 대기가 길었던 곳 (REC-012)
 */
public record RecommendationResponse(OrderResponse order, long emptyMeters, int fuelCost, int netProfit, boolean longWait) {
}
