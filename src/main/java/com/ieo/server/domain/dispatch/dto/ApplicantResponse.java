package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.time.Instant;

/**
 * 주선사가 보는 요청한 차주 (DSP-021). 이행 기록은 Ieo에서 완료한 운행 수와
 * 상차 예정 2시간 이내에 차주가 취소한 수 (DSP-031).
 * TODO: 현재 위치·상차지까지 거리는 운행 위치 기록(LocationPoint)을 만든 뒤 붙인다
 */
public record ApplicantResponse(
		Long requestId,
		DispatchRequestStatus status,
		String driverName,
		String plateNumber,
		Tonnage tonnage,
		String tonnageLabel,
		BodyType bodyType,
		String bodyTypeLabel,
		long completedTripCount,
		long lateCancelCount,
		Instant requestedAt,
		Instant expiresAt) {

	public static ApplicantResponse of(DispatchRequest request, Instant now, long completedTripCount,
			long lateCancelCount) {
		Vehicle vehicle = request.getVehicle();
		return new ApplicantResponse(request.getId(), request.statusAt(now), request.getDriver().getUser().getName(),
				vehicle.getPlateNumber(), vehicle.getTonnage(), vehicle.getTonnage().getLabel(),
				vehicle.getBodyType(), vehicle.getBodyType().getLabel(), completedTripCount, lateCancelCount,
				request.getCreatedAt(), request.expiresAt());
	}
}
