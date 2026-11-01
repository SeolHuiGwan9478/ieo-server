package com.ieo.server.domain.vehicle.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 톤수. 화물 앱들이 흔히 나누는 단위를 따른다.
 * 오더(Order)도 같은 enum을 써서 차량과 바로 비교한다.
 */
@Getter
@RequiredArgsConstructor
public enum Tonnage {
	TON_1("1톤"),
	TON_1_4("1.4톤"),
	TON_2_5("2.5톤"),
	TON_3_5("3.5톤"),
	TON_5("5톤"),
	TON_5_AXLE("5톤 축"),
	TON_8("8톤"),
	TON_11("11톤"),
	TON_14("14톤"),
	TON_18("18톤"),
	TON_25("25톤");

	private final String label;
}
