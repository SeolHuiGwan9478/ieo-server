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
	TON_1("1톤", 8.5),
	TON_1_4("1.4톤", 8.0),
	TON_2_5("2.5톤", 7.5),
	TON_3_5("3.5톤", 6.5),
	TON_5("5톤", 5.0),
	TON_5_AXLE("5톤 축", 4.7),
	TON_8("8톤", 4.5),
	TON_11("11톤", 4.2),
	TON_14("14톤", 4.0),
	TON_18("18톤", 3.6),
	TON_25("25톤", 3.3);

	private final String label;

	/**
	 * Pleos 실측도, 차주가 넣은 연비도 없을 때 쓰는 기본 연비(km/L, 경유·짐 실은 평균). 배차 추천 연료비 계산에 쓴다 (REC-011).
	 * 3.5톤 넘는 화물차는 공인 연비가 없다. 국토부 2016 공개값(25톤 최고 3.7, 14톤 최고 4.5), 업계 기사(5톤 카고 4.5),
	 * 포터 디젤 8.8을 기준으로 평균 쪽으로 잡은 값이다 (docs/design/04_배차추천.md). 아버님 확인 뒤 고친다
	 */
	private final double defaultKmPerLiter;

	/** 이 톤수 차량이 required 톤수 오더를 맡을 수 있는지. 같거나 크면 된다 */
	public boolean canCarry(Tonnage required) {
		return compareTo(required) >= 0;
	}
}
