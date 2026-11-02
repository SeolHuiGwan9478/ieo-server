package com.ieo.server.domain.dispatch.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상하차 방식 (DSP-001) */
@Getter
@RequiredArgsConstructor
public enum LoadingMethod {
	FORKLIFT("지게차"),
	MANUAL("수작업");

	private final String label;
}
