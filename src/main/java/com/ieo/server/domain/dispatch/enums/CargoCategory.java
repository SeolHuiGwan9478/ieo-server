package com.ieo.server.domain.dispatch.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 화물 종류 (DSP-001). 차주가 "제외 화물"로 거를 수 있게 정해진 목록에서 고른다 (REC-004).
 * 자세한 품목은 Order.cargoType(자유 입력)에 적는다. 예: 일반 + "음료 파레트 12개"
 */
@Getter
@RequiredArgsConstructor
public enum CargoCategory {
	GENERAL("일반"),
	/** 냉동·냉장 차(BodyType.REFRIGERATED)로만 나를 수 있다 */
	COLD("냉동·냉장"),
	/** 짐을 직접 나르는 일이 많아 피하는 차주가 많다 */
	MOVING("이사"),
	/** 운송 자격이 필요하다. 자격 확인은 아직 없고 표시만 한다 */
	HAZARDOUS("위험물");

	private final String label;
}
