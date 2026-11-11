package com.ieo.server.domain.dispatch.dto;

/**
 * 배차 요청. 차량을 비우면 기본 차량.
 *
 * @param isRecommendation 차량 앱의 배차 추천에서 눌러 보낸 요청이면 true. 비우면 false
 */
public record CreateDispatchRequestRequest(Long vehicleId, Boolean isRecommendation) {

	public CreateDispatchRequestRequest(Long vehicleId) {
		this(vehicleId, null);
	}

	public boolean fromRecommendation() {
		return Boolean.TRUE.equals(isRecommendation);
	}
}
