package com.ieo.server.domain.site.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차주가 남기는 상하차지 정보 항목 (SITE-004) */
@Getter
@RequiredArgsConstructor
public enum SiteAttributeKey {
	/** 진입 높이 제한(m). 값은 "3.50"처럼 소수 둘째 자리까지 */
	HEIGHT_LIMIT("높이 제한"),
	/** 지게차 유무. 값은 "true" / "false" */
	FORKLIFT("지게차"),
	/** 대기 중 주차 가능 여부. 값은 "true" / "false" */
	PARKING("주차");

	private final String label;
}
