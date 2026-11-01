package com.ieo.server.domain.vehicle.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차종(적재함 형태). 오더 조건(윙바디만, 냉동만 등)과 비교한다. */
@Getter
@RequiredArgsConstructor
public enum BodyType {
	CARGO("카고"),
	WING("윙바디"),
	BOX("탑차"),
	REFRIGERATED("냉동·냉장"),
	LIFT("리프트"),
	ETC("기타");

	private final String label;
}
