package com.ieo.server.domain.vehicle.enums;

/** 기름값 계산에 쓴 연비가 어디서 왔는지. 화면에 "기준 연비"로 보여 줄 때 쓴다 */
public enum FuelEconomySource {
	/** Pleos 차량이 잰 누적 연비 */
	PLEOS,
	/** 차주가 휴대폰 앱에 넣은 평소 연비 */
	MANUAL,
	/** 톤수별 기본값 */
	DEFAULT
}
