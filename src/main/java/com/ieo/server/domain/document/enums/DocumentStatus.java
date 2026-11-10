package com.ieo.server.domain.document.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 서류 한 건의 상태. 다시 내면 새 건이 PENDING으로 생긴다 (07 인계서 5장) */
@Getter
@RequiredArgsConstructor
public enum DocumentStatus {
	PENDING("심사 대기"),
	APPROVED("승인"),
	REJECTED("반려");

	private final String label;
}
