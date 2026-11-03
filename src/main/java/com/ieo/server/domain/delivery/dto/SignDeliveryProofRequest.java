package com.ieo.server.domain.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 수하인 서명. signature는 서명 캔버스의 PNG data URL(data:image/png;base64,...).
 * 위치는 브라우저 위치 동의를 했을 때만 보낸다
 */
public record SignDeliveryProofRequest(
		@Size(max = 30) String signerName,
		@NotBlank String signature,
		@Size(max = 500) String issueNote,
		Double lat,
		Double lng) {
}
