package com.ieo.server.domain.dispatch.enums;

/**
 * 오더 상태. "요청 받음"은 대기 중인 요청이 있는지로 알 수 있어 저장하지 않고,
 * 상차 시간이 지나 마감된 것은 pickupUntil로 판단한다. CLOSED는 주선사가 직접 내린 경우만.
 */
public enum OrderStatus {
	/** 모집 중 */
	OPEN,
	/** 배차 완료 */
	ASSIGNED,
	/** 주선사가 내림 */
	CLOSED
}
