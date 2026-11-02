package com.ieo.server.domain.trip.dto;

import com.ieo.server.domain.trip.enums.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 직접 등록 (TRIP-001). 차량을 비우면 기본 차량으로 등록한다.
 * 주선사는 Ieo에 있으면 brokerId, 없으면 brokerName(선택)
 */
public record CreateTripRequest(
		@NotNull @Valid TripStopRequest pickup,
		@NotNull @Valid TripStopRequest dropoff,
		@NotNull Integer fare,
		@NotNull PaymentType paymentType,
		Long vehicleId,
		Long brokerId,
		@Size(max = 50) String brokerName) {
}
