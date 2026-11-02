package com.ieo.server.domain.dispatch.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 배차 확정 뒤 취소 사유 (DSP-030) */
@Getter
@RequiredArgsConstructor
public enum CancelReason {
	VEHICLE_PROBLEM("차량 고장·사고"),
	SCHEDULE_CONFLICT("일정이 겹침"),
	SHIPPER_CANCELED("화주가 취소함"),
	CONDITION_CHANGED("운임·화물 조건이 바뀜"),
	OTHER("기타");

	private final String label;
}
