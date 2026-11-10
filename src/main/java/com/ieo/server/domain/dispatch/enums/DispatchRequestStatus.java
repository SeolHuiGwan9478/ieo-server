package com.ieo.server.domain.dispatch.enums;

/**
 * 배차 요청 상태. EXPIRED는 보통 시각으로 판단하고(요청 후 10분, DSP-023), 같은 오더를 다시 요청할 때만 저장한다
 */
public enum DispatchRequestStatus {
	/** 주선사 확정을 기다리는 중 */
	PENDING,
	/** 확정됨. 운행이 생긴다 */
	CONFIRMED,
	/** 다른 차주가 확정되어 마감 */
	DECLINED,
	/** 차주가 철회 */
	WITHDRAWN,
	/** 확정된 뒤 차주나 주선사가 취소 (DSP-030) */
	CANCELED,
	/** 10분 안에 확정되지 않음 */
	EXPIRED
}
