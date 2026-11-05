package com.ieo.server.domain.vehicle.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 톤수. 화물 앱들이 흔히 나누는 단위를 따른다. 작은 것부터 큰 순서로 둔다 (canCarry가 이 순서를 쓴다).
 * 오더(Order)도 같은 enum을 써서 차량과 바로 비교한다.
 */
@Getter
@RequiredArgsConstructor
public enum Tonnage {
	TON_1("1톤", 9.0),
	TON_1_4("1.4톤", 8.0),
	TON_2_5("2.5톤", 7.0),
	TON_3_5("3.5톤", 6.0),
	TON_5("5톤", 4.5),
	TON_5_AXLE("5톤 축", 4.2),
	TON_8("8톤", 3.8),
	TON_11("11톤", 3.5),
	TON_14("14톤", 3.2),
	TON_18("18톤", 3.0),
	TON_25("25톤", 2.8);

	private final String label;

	/**
	 * 실측 연비가 없을 때 쓰는 기본 연비(km/L). 배차 추천 연료비 계산에 쓴다 (REC-011).
	 * 가안: 아버님 인터뷰로 확인할 값 (docs/design/04_배차추천.md)
	 */
	private final double defaultKmPerLiter;

	/** 이 톤수 차량이 required 톤수 오더를 맡을 수 있는지. 같거나 크면 된다 */
	public boolean canCarry(Tonnage required) {
		return compareTo(required) >= 0;
	}
}
