package com.ieo.server.domain.recommendation.dto;

import java.util.List;

/** 세운 자리 근처 추천, 실수익 높은 순 최대 3건 (REC-001, 002). 추천을 껐거나 기본 차량이 없으면 비어 있다 */
public record RecommendationListResponse(List<RecommendationResponse> items) {

	public static RecommendationListResponse none() {
		return new RecommendationListResponse(List.of());
	}
}
