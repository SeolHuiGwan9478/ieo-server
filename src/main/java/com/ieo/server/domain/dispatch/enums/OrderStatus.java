package com.ieo.server.domain.dispatch.enums;

/**
 * 오더 상태. "요청 받음"은 대기 중인 요청이 있는지로 알 수 있어 저장하지 않는다.
 * 상차 시간이 지난 모집 중 오더는 OrderExpirer가 주기적으로 CLOSED로 바꾼다 (이유는 Order.closeReason).
 * 그 사이(최대 몇 분)에는 OPEN이지만 isOpenAt(now)가 false라 차주에게는 안 보인다
 */
public enum OrderStatus {
	/** 모집 중 */
	OPEN,
	/** 배차 완료 */
	ASSIGNED,
	/** 마감. 주선사가 내렸거나, 상차 시간이 지났거나, 배차 취소 뒤 내림 (OrderCloseReason) */
	CLOSED
}
