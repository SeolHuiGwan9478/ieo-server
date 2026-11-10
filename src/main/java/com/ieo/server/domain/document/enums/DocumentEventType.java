package com.ieo.server.domain.document.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서류 처리 이력의 종류. 열람은 운영자가 서류 사진을 열 때마다 남긴다 (07 인계서 8장).
 * 값을 더하면 기존 DB의 CHECK 제약도 고친다 (scripts/driver-documents-business-check.sql)
 */
@Getter
@RequiredArgsConstructor
public enum DocumentEventType {
	SUBMITTED("제출"),
	/** 사업자등록증의 국세청 진위확인. 제출 때 자동으로, 운영자가 "다시 조회"하면 또 남는다 */
	BUSINESS_CHECKED("국세청 조회"),
	VIEWED("열람"),
	APPROVED("승인"),
	REJECTED("반려");

	private final String label;
}
