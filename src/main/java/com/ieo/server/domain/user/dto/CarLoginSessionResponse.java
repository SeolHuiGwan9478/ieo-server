package com.ieo.server.domain.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * pollSecret은 이 응답에서 한 번만 준다. 차량 앱은 메모리에만 들고 있고, 화면·QR에 넣지 않는다.
 * qrUrl에는 code만 들어 있다.
 */
public record CarLoginSessionResponse(
		UUID sessionId,
		String code,
		String pollSecret,
		String qrUrl,
		Instant expiresAt,
		int pollIntervalSeconds) {
}
