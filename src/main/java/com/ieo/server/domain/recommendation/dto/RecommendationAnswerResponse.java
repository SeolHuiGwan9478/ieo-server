package com.ieo.server.domain.recommendation.dto;

import com.ieo.server.domain.dispatch.dto.DispatchRequestResponse;
import com.ieo.server.domain.recommendation.enums.RecommendationAnswer;

/** 수락(ACCEPTED)이면 보낸 배차 요청을 같이 준다. 자동 확정 오더면 그 안에 새 운행 id가 있다 */
public record RecommendationAnswerResponse(Long orderId, RecommendationAnswer answer, DispatchRequestResponse dispatchRequest) {
}
