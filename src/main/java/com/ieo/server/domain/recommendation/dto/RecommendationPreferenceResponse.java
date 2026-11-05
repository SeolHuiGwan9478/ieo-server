package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.recommendation.entity.RecommendationPreference;

public record RecommendationPreferenceResponse(boolean enabled, int radiusKm, Integer minNetProfit) {

	public static RecommendationPreferenceResponse from(RecommendationPreference preference) {
		return new RecommendationPreferenceResponse(
				preference.isEnabled(), preference.getRadiusKm(), preference.getMinNetProfit());
	}
}
