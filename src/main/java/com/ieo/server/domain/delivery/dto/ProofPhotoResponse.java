package com.ieo.server.domain.delivery.dto;

import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import java.time.Instant;

/** path는 사진 이미지를 받는 공개 주소 */
public record ProofPhotoResponse(Long id, ProofPhotoType type, String path, Instant uploadedAt) {

	public static ProofPhotoResponse of(ProofPhoto photo, String token) {
		return new ProofPhotoResponse(photo.getId(), photo.getType(),
				"/api/public/delivery-proofs/" + token + "/photos/" + photo.getId(), photo.getCreatedAt());
	}
}
