package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.entity.CarSession;
import java.time.Instant;
import java.util.UUID;

public record CarSessionResponse(UUID id, String deviceName, String appVersion, Instant loggedInAt, Instant lastUsedAt) {

	public static CarSessionResponse from(CarSession session) {
		return new CarSessionResponse(session.getId(), session.getDeviceName(), session.getAppVersion(),
				session.getCreatedAt(), session.getLastUsedAt());
	}
}
