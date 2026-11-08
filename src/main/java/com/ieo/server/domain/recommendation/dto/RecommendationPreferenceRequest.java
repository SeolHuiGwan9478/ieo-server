package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.dispatch.enums.CargoCategory;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * 추천 설정. minNetProfit을 비우면 조건 없음.
 * excludedCargoCategories(REC-004)를 비우면(null) 제외 화물은 그대로 두고, 빈 목록이면 모두 받는다
 */
public record RecommendationPreferenceRequest(@NotNull Boolean enabled, @NotNull Integer radiusKm, Integer minNetProfit,
		Set<CargoCategory> excludedCargoCategories) {
}
