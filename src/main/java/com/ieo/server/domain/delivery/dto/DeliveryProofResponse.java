package com.ieo.server.domain.delivery.dto;

import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import java.time.Instant;
import java.util.List;

/**
 * 차주가 보는 인수증. required는 결제 방식이 인수증인 운행인지(아니면 인수증을 받지 않는다).
 * canUpload는 지금 사진을 올릴 수 있는지: 인수증 결제, 하차지 도착 뒤, 아직 안 보냄, 사진 5장 미만
 */
public record DeliveryProofResponse(
		Long tripId,
		boolean required,
		DeliveryProofStatus status,
		Instant sentAt,
		List<ProofPhotoResponse> photos,
		int maxPhotos,
		boolean canUpload) {
}
