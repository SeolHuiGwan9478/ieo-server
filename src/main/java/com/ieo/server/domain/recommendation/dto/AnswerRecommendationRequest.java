package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.recommendation.enums.RecommendationAnswer;

/** 안내한 추천과 차주의 대답. answer를 비우면 안내만 하고 대답이 없었던 것으로 적는다 */
public record AnswerRecommendationRequest(RecommendationAnswer answer) {
}
