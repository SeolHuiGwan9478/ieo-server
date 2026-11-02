package com.ieo.server.domain.dispatch.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 차주의 오더 신고 유형 (DSP-042) */
@Getter
@RequiredArgsConstructor
public enum ReportType {
	FALSE_INFO("허위 오더"),
	OVERLOAD("과적 요구"),
	UNPAID("운임 미지급"),
	MISMATCH("정보가 실제와 다름");

	private final String label;
}
