package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.config.WebConfig;
import java.time.Instant;

/**
 * 주선사 콘솔의 운행 한 건 (BRK-020, 021). 우리 회사 오더로 배차된 운행만 온다.
 *
 * 위치 규칙 (BRK-002): 단골 차주(linkedDriver)일 때만 locationVisible이고, 그때만 위치를 채운다.
 * 차주 번호도 단골일 때만 준다 (단골 목록에서 이미 보이는 번호).
 * 지금은 차량 앱이 위치를 보내지 않고 ETA도 계산하지 않아서 location·etaAt은 항상 null이다
 * (원작자와 형식을 정한 뒤 채운다. 06 문서 6장).
 * receipt는 수하인이 서명했거나 차주가 종이 인수증 사진을 올린 뒤에만 있다
 */
public record BrokerTripResponse(
		Long id,
		Long orderId,
		TripStatus status,
		String statusLabel,
		Long driverUserId,
		String driverName,
		String driverPhone,
		boolean linkedDriver,
		String plateNumber,
		Tonnage tonnage,
		String tonnageLabel,
		boolean locationVisible,
		Location location,
		Instant etaAt,
		int fare,
		PaymentType paymentType,
		String paymentTypeLabel,
		BrokerTripStopResponse pickup,
		BrokerTripStopResponse dropoff,
		Receipt receipt,
		Instant startedAt,
		Instant completedAt,
		Instant canceledAt,
		Instant createdAt) {

	/** 차량의 마지막 위치. 아직 받지 않는다 */
	public record Location(double lat, double lng, Instant recordedAt) {
	}

	/** 전자인수증. path(/r/토큰)를 콘솔 주소 뒤에 붙이면 인수증 페이지다. 링크는 운행 완료 7일 뒤 만료 */
	public record Receipt(
			DeliveryProofStatus status,
			String signerName,
			Instant signedAt,
			boolean hasPaperPhoto,
			String issueNote,
			String path,
			Instant expiresAt) {

		/** 서명 전이고 종이 사진도 없으면 null */
		static Receipt of(DeliveryProof proof, boolean hasPaperPhoto) {
			if (proof == null || (!proof.isSigned() && !hasPaperPhoto)) {
				return null;
			}
			return new Receipt(proof.status(hasPaperPhoto), proof.getSignerName(), proof.getSignedAt(), hasPaperPhoto,
					proof.getIssueNote(), WebConfig.RECEIPT_PAGE + "/" + proof.getToken(), proof.expiresAt());
		}
	}

	public static BrokerTripResponse of(Trip trip, boolean linkedDriver,
			BrokerTripStopResponse pickup, BrokerTripStopResponse dropoff,
			DeliveryProof deliveryProof, boolean hasPaperPhoto) {
		User user = trip.getDriver().getUser();
		Vehicle vehicle = trip.getVehicle();
		return new BrokerTripResponse(
				trip.getId(),
				trip.getOrder() == null ? null : trip.getOrder().getId(),
				trip.getStatus(),
				statusLabel(trip.getStatus()),
				trip.getDriver().getUserId(),
				user.getName(),
				linkedDriver ? user.getPhone() : null,
				linkedDriver,
				vehicle.getPlateNumber(),
				vehicle.getTonnage(),
				vehicle.getTonnage().getLabel(),
				linkedDriver,
				null,
				null,
				trip.getFare(),
				trip.getPaymentType(),
				trip.getPaymentType().getLabel(),
				pickup,
				dropoff,
				Receipt.of(deliveryProof, hasPaperPhoto),
				trip.getStartedAt(),
				trip.getCompletedAt(),
				trip.getCanceledAt(),
				trip.getCreatedAt());
	}

	private static String statusLabel(TripStatus status) {
		return switch (status) {
			case SCHEDULED -> "출발 전";
			case TO_PICKUP -> "상차지로 이동 중";
			case AT_PICKUP -> "상차지 도착";
			case IN_TRANSIT -> "운송 중";
			case AT_DROPOFF -> "하차지 도착";
			case COMPLETED -> "완료";
			case CANCELLED -> "취소";
		};
	}
}
