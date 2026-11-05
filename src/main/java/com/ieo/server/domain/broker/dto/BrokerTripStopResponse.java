package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitStatus;
import com.ieo.server.global.config.WebConfig;
import java.time.Instant;

/**
 * 운행의 상차지·하차지 한 곳 (BRK-020, 021).
 * 도착·작업 시작·떠난 시각은 진행 상황이라 바로 보인다 (아직 오지 않았으면 null).
 * 대기 시간·대기비는 차주가 대기 증빙 링크를 만든 뒤에만 waitProof로 보인다.
 * 차주가 시각을 고치기 전에 금액이 먼저 보이면 다툼이 생길 수 있어서다
 */
public record BrokerTripStopResponse(
		StopType type,
		String siteName,
		String address,
		double lat,
		double lng,
		Instant plannedAt,
		WaitStatus waitStatus,
		Instant arrivedAt,
		Instant workStartedAt,
		Instant departedAt,
		boolean edited,
		WaitProofSummary waitProof) {

	/**
	 * 차주가 만든 대기 증빙. path(/w/토큰)를 콘솔 주소 뒤에 붙이면 증빙 페이지다 (인쇄해서 PDF로 저장).
	 * 분·금액은 작업 시작이 없거나 대기비 정책이 없으면 null
	 */
	public record WaitProofSummary(
			String path,
			Instant issuedAt,
			Instant expiresAt,
			Long waitMinutes,
			Long chargeableWaitMinutes,
			Integer chargeableAmount,
			Long workMinutes,
			Long stayMinutes) {

		static WaitProofSummary of(WaitRecord record, WaitProof proof) {
			return new WaitProofSummary(
					WebConfig.WAIT_PROOF_PAGE + "/" + proof.getShareToken(),
					proof.getCreatedAt(),
					proof.getExpiresAt(),
					record.waitMinutes(),
					record.chargeableWaitMinutes(),
					record.chargeableAmount(),
					record.workMinutes(),
					record.stayMinutes());
		}
	}

	/** record·proof는 없으면 null */
	public static BrokerTripStopResponse of(TripStop stop, WaitRecord record, WaitProof proof) {
		return new BrokerTripStopResponse(
				stop.getType(),
				stop.getSite().getName(),
				stop.getSite().getAddress(),
				stop.getSite().getLat(),
				stop.getSite().getLng(),
				stop.getPlannedAt(),
				record == null ? null : record.status(),
				record == null ? null : record.getArrivedAt(),
				record == null ? null : record.getWorkStartedAt(),
				record == null ? null : record.getDepartedAt(),
				record != null && record.isEdited(),
				record == null || proof == null ? null : WaitProofSummary.of(record, proof));
	}
}
