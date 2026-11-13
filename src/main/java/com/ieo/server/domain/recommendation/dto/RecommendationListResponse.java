package com.ieo.server.domain.recommendation.dto;

import java.util.List;

/**
 * 추천 최대 1건 (REC-001, 002). 추천을 껐거나 기본 차량이 없거나 추천하지 않는 때면 비어 있다.
 *
 * @param fresh 이번에 새로 계산했으면 true. 같은 곳에서 이미 준 것을 다시 돌려주면 false라 차량 앱은 큰 창을 띄우지 않는다
 */
public record RecommendationListResponse(List<RecommendationResponse> items, boolean fresh) {

	public static RecommendationListResponse none() {
		return new RecommendationListResponse(List.of(), false);
	}
}
