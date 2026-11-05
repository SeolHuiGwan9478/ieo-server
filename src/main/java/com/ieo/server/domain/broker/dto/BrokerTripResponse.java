package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripPosition;
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
 * 위치 규칙 (BRK-002): 단골 차주(linkedDriver)일 때만 locationVisible이고, 그때만 location을 채운다.
 * 차주 번호도 단골일 때만 준다 (단골 목록에서 이미 보이는 번호).
 * etaAt·progressPercent는 위치가 아니라서 모두에게 준다.
 * 셋 다 운행 중 마지막 위치(TripPosition, 운행마다 한 줄)에서 읽고, 위치가 아직 안 왔거나 상하차지에 머무는 중이면 null이다.
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
		Integer progressPercent,
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

	/** 차량의 마지막 위치와 차에서 잰 시각 */
	public record Location(double lat, double lng, Instant recordedAt) {

		static Location of(TripPosition position) {
			return new Location(position.getLat(), position.getLng(), position.getRecordedAt());
		}
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

	/** position은 아직 위치가 없으면 null */
	public static BrokerTripResponse of(Trip trip, boolean linkedDriver, TripPosition position,
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
				linkedDriver && position != null ? Location.of(position) : null,
				position == null ? null : position.eta().orElse(null),
				position == null ? null : position.progressPercent().orElse(null),
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
