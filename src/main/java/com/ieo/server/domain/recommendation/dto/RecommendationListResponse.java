package com.ieo.server.domain.recommendation.dto;

import java.time.Instant;
import java.util.List;

/**
 * 지금 운행 다음에 이어서 할 배차 후보, 실수익 높은 순 최대 5건 (REC-013, REC-021).
 *
 * @param freeAt 하차가 끝날 예정 시각 (REC-002). 추천하지 않는 상태면 null
 */
public record RecommendationListResponse(Instant freeAt, List<RecommendationResponse> items) {

	public static RecommendationListResponse none() {
		return new RecommendationListResponse(null, List.of());
	}
}
