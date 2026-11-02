package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitStatus;
import java.time.Instant;

/**
 * 대기 기록과 계산값. 분과 금액은 아직 정해지지 않았으면 null
 * (예: 작업 시작 전이면 waitMinutes·chargeableAmount가 null).
 * plannedAtMissing이면 예정 시각이 없어 진입 시각부터 대기비를 셌다는 뜻이다
 */
public record WaitRecordResponse(
		Long id,
		StopType stopType,
		Long siteId,
		String siteName,
		WaitStatus status,
		Instant plannedAt,
		boolean plannedAtMissing,
		Instant arrivedAt,
		Instant workStartedAt,
		Instant departedAt,
		Instant chargeableFrom,
		Long waitMinutes,
		Long chargeableWaitMinutes,
		Long workMinutes,
		Long stayMinutes,
		Integer chargeableAmount,
		WaitPolicyResponse policy,
		boolean edited,
		Instant editedAt,
		Original original) {

	/** 처음 고치기 전 값. 고친 적이 없으면 null */
	public record Original(Instant arrivedAt, Instant workStartedAt, Instant departedAt) {
	}

	public static WaitRecordResponse from(WaitRecord record) {
		TripStop stop = record.getTripStop();
		Original original = record.isEdited()
				? new Original(record.getOriginalArrivedAt(), record.getOriginalWorkStartedAt(), record.getOriginalDepartedAt())
				: null;
		return new WaitRecordResponse(
				record.getId(),
				stop.getType(),
				stop.getSite().getId(),
				stop.getSite().getName(),
				record.status(),
				stop.getPlannedAt(),
				stop.getPlannedAt() == null,
				record.getArrivedAt(),
				record.getWorkStartedAt(),
				record.getDepartedAt(),
				record.chargeableFrom(),
				record.waitMinutes(),
				record.chargeableWaitMinutes(),
				record.workMinutes(),
				record.stayMinutes(),
				record.chargeableAmount(),
				WaitPolicyResponse.from(record.getPolicy()),
				record.isEdited(),
				record.getEditedAt(),
				original);
	}
}
