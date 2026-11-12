package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.delivery.dto.ProofPhotoResponse;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripPosition;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.config.WebConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 주선사 콘솔의 운행 한 건 (BRK-020, 021). 우리 회사 오더로 배차된 운행만 온다.
 *
 * 위치 규칙 (BRK-002): 단골 차주(linkedDriver)일 때만 locationVisible이고, 그때만 location을 채운다.
 * 차주 번호도 단골일 때만 준다 (단골 목록에서 이미 보이는 번호).
 * etaAt·progressPercent는 위치가 아니라서 단골이 아니어도 준다. 차주가 주선사 공유 동의를 끄면(ACC-006) 위치·ETA·진행률 모두 주지 않는다.
 * 셋 다 운행 중 마지막 위치(TripPosition, 운행마다 한 줄)에서 읽고, 위치가 아직 안 왔거나 상하차지에 머무는 중이면 null이다.
 * receipt는 차주가 종이 인수증 사진을 보낸 뒤에만 있다. 결제 방식이 인수증인데 receipt가 없으면 아직 안 보낸 것이다.
 * fare는 지급운임(차주). billedFare(청구운임)·fee(수수료)·feeRate(%)는 오더에 청구운임을 적었을 때만 있다 (주선사 콘솔 전용)
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
		Integer billedFare,
		Integer fee,
		BigDecimal feeRate,
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

	/** 차주가 보낸 종이 인수증 사진. 사진 path에 로그인 토큰을 붙여 받는다 */
	public record Receipt(Instant sentAt, List<ProofPhotoResponse> photos) {

		/** 아직 보내지 않았으면 null */
		static Receipt of(Trip trip, DeliveryProof proof, List<ProofPhoto> photos) {
			if (proof == null || !proof.isSent()) {
				return null;
			}
			return new Receipt(proof.getSentAt(),
					photos.stream().map(photo -> ProofPhotoResponse.forBroker(photo, trip.getId())).toList());
		}
	}

	/** position은 아직 위치가 없으면 null */
	public static BrokerTripResponse of(Trip trip, boolean linkedDriver, boolean sharing, TripPosition shared,
			BrokerTripStopResponse pickup, BrokerTripStopResponse dropoff,
			DeliveryProof deliveryProof, List<ProofPhoto> receiptPhotos) {
		User user = trip.getDriver().getUser();
		Vehicle vehicle = trip.getVehicle();
		TripPosition position = sharing ? shared : null;
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
				linkedDriver && sharing,
				linkedDriver && position != null ? Location.of(position) : null,
				position == null ? null : position.eta().orElse(null),
				position == null ? null : position.progressPercent().orElse(null),
				trip.getFare(),
				trip.getOrder() == null ? null : trip.getOrder().getBilledFare(),
				trip.getOrder() == null ? null : trip.getOrder().fee(),
				trip.getOrder() == null ? null : trip.getOrder().feeRate(),
				trip.getPaymentType(),
				trip.getPaymentType().getLabel(),
				pickup,
				dropoff,
				Receipt.of(trip, deliveryProof, receiptPhotos),
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
