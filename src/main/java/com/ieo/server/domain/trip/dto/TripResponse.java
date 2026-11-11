package com.ieo.server.domain.trip.dto;

import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import java.time.Instant;

/** brokerName은 Ieo 주선사면 상호, 아니면 차주가 적은 이름 */
public record TripResponse(
		Long id,
		TripStatus status,
		int fare,
		PaymentType paymentType,
		String paymentTypeLabel,
		TripSource source,
		Long brokerId,
		String brokerName,
		Long vehicleId,
		String plateNumber,
		TripStopResponse pickup,
		TripStopResponse dropoff,
		java.time.Instant etaSharedAt,
		Instant startedAt,
		Instant completedAt,
		Instant canceledAt,
		Instant createdAt) {

	public static TripResponse from(Trip trip) {
		Long brokerId = trip.getBroker() == null ? null : trip.getBroker().getId();
		String brokerName = trip.getBroker() == null ? trip.getBrokerName() : trip.getBroker().getName();
		return new TripResponse(
				trip.getId(),
				trip.getStatus(),
				trip.getFare(),
				trip.getPaymentType(),
				trip.getPaymentType().getLabel(),
				trip.getSource(),
				brokerId,
				brokerName,
				trip.getVehicle().getId(),
				trip.getVehicle().getPlateNumber(),
				TripStopResponse.from(trip.pickup()),
				TripStopResponse.from(trip.dropoff()),
				trip.getEtaSharedAt(),
				trip.getStartedAt(),
				trip.getCompletedAt(),
				trip.getCanceledAt(),
				trip.getCreatedAt());
	}
}
