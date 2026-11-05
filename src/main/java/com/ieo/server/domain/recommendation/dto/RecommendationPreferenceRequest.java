package com.ieo.server.domain.recommendation.dto;

import jakarta.validation.constraints.NotNull;

/** 추천 설정. minNetProfit을 비우면 조건 없음 */
public record RecommendationPreferenceRequest(@NotNull Boolean enabled, @NotNull Integer radiusKm, Integer minNetProfit) {
}
