package com.ieo.server.domain.trip.enums;

/** 운행이 어디서 생겼는지 (TRIP-007). Ieo 배차 운행만 노선 평균 운임 통계에 쓴다 */
public enum TripSource {
	/** Ieo 오더 마켓 배차 확정 */
	IEO_DISPATCH,
	/** 다른 앱·전화로 받은 일을 화면으로 직접 등록 */
	MANUAL,
	/** 음성으로 등록 */
	VOICE
}
