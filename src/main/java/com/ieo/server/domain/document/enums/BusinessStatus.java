package com.ieo.server.domain.document.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 국세청 사업자 상태 (응답의 b_stt_cd). 계속사업자만 승인 대상이다 */
@Getter
@RequiredArgsConstructor
public enum BusinessStatus {
	ACTIVE("01", "계속사업자"),
	SUSPENDED("02", "휴업자"),
	CLOSED("03", "폐업자");

	private final String code;
	private final String label;

	public static BusinessStatus fromCode(String code) {
		return Arrays.stream(values()).filter(status -> status.code.equals(code)).findFirst().orElse(null);
	}
}
