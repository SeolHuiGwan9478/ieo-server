package com.ieo.server.domain.delivery.dto;

import com.ieo.server.domain.delivery.entity.ProofPhoto;
import java.time.Instant;

/** path는 사진 이미지 주소. 로그인 토큰을 붙여 받는다 (차주는 /api/trips 아래, 주선사는 /api/brokers 아래) */
public record ProofPhotoResponse(Long id, String path, Instant uploadedAt) {

	public static ProofPhotoResponse forDriver(ProofPhoto photo, Long tripId) {
		return new ProofPhotoResponse(photo.getId(),
				"/api/trips/" + tripId + "/delivery-proof/photos/" + photo.getId(), photo.getCreatedAt());
	}

	public static ProofPhotoResponse forBroker(ProofPhoto photo, Long tripId) {
		return new ProofPhotoResponse(photo.getId(),
				"/api/brokers/me/trips/" + tripId + "/receipt-photos/" + photo.getId(), photo.getCreatedAt());
	}
}
