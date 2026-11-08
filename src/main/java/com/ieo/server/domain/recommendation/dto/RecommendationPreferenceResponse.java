package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.recommendation.entity.RecommendationPreference;
import java.util.Arrays;
import java.util.List;

/** excludedCargoCategories는 목록 순서(일반, 냉동·냉장, 이사, 위험물)로 준다 */
public record RecommendationPreferenceResponse(boolean enabled, int radiusKm, Integer minNetProfit,
		List<CargoCategory> excludedCargoCategories) {

	public static RecommendationPreferenceResponse from(RecommendationPreference preference) {
		List<CargoCategory> excluded = Arrays.stream(CargoCategory.values()).filter(preference::excludes).toList();
		return new RecommendationPreferenceResponse(
				preference.isEnabled(), preference.getRadiusKm(), preference.getMinNetProfit(), excluded);
	}
}
