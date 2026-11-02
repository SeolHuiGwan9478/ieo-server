package com.ieo.server.domain.trip.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운임 결제 방식 (TRIP-001) */
@Getter
@RequiredArgsConstructor
public enum PaymentType {
	PREPAID("선불"),
	COLLECT("착불"),
	/** 인수증을 주선사에 내고 정산 주기에 맞춰 받는다 */
	RECEIPT("인수증");

	private final String label;
}
