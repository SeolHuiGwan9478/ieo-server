package com.ieo.server.domain.delivery.dto;

import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.global.config.WebConfig;
import java.time.Instant;
import java.util.List;

/**
 * 인수증. 차주(차량 앱)와 수하인(웹 페이지)이 같이 본다.
 * path는 수하인 서명 페이지 주소(차량 화면 QR에 넣는다). 사진·서명 이미지는 /api/public 아래 주소다. cargoType·weightKg는 Ieo 배차 운행만 있다
 */
public record DeliveryProofResponse(
		String token,
		String path,
		DeliveryProofStatus status,
		Long tripId,
		String pickupSiteName,
		String dropoffSiteName,
		String dropoffAddress,
		String plateNumber,
		String brokerName,
		String cargoType,
		Integer weightKg,
		String signerName,
		Instant signedAt,
		String signaturePath,
		String issueNote,
		List<ProofPhotoResponse> photos,
		Instant expiresAt) {

	public static DeliveryProofResponse of(DeliveryProof proof, DeliveryProofStatus status, List<ProofPhotoResponse> photos) {
		Trip trip = proof.getTrip();
		Order order = trip.getOrder();
		String base = "/api/public/delivery-proofs/" + proof.getToken();
		return new DeliveryProofResponse(
				proof.getToken(),
				WebConfig.RECEIPT_PAGE + "/" + proof.getToken(),
				status,
				trip.getId(),
				trip.pickup().getSite().getName(),
				trip.dropoff().getSite().getName(),
				trip.dropoff().getSite().getAddress(),
				trip.getVehicle().getPlateNumber(),
				trip.getBroker() == null ? trip.getBrokerName() : trip.getBroker().getName(),
				order == null ? null : order.getCargoType(),
				order == null ? null : order.getWeightKg(),
				proof.getSignerName(),
				proof.getSignedAt(),
				proof.isSigned() ? base + "/signature" : null,
				proof.getIssueNote(),
				photos,
				proof.expiresAt());
	}
}
