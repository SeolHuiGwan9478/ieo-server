package com.ieo.server.domain.ledger.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 경비 분류 (LEDG-011) */
@Getter
@RequiredArgsConstructor
public enum ExpenseCategory {
	FUEL("주유"),
	TOLL("통행료"),
	MEAL("식대"),
	MAINTENANCE("정비"),
	INSURANCE("보험"),
	PARKING("주차"),
	ETC("기타");

	private final String label;
}
