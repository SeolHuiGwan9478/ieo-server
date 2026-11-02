package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import java.time.Instant;

/** 차주가 보는 내 배차 요청. 확정되면 tripId로 생긴 운행을 연다 */
public record DispatchRequestResponse(
		Long id,
		Long orderId,
		DispatchRequestStatus status,
		Long vehicleId,
		String plateNumber,
		Instant createdAt,
		Instant expiresAt,
		Long tripId) {

	public static DispatchRequestResponse of(DispatchRequest request, Instant now, Long tripId) {
		return new DispatchRequestResponse(request.getId(), request.getOrder().getId(), request.statusAt(now),
				request.getVehicle().getId(), request.getVehicle().getPlateNumber(),
				request.getCreatedAt(), request.expiresAt(), tripId);
	}
}
