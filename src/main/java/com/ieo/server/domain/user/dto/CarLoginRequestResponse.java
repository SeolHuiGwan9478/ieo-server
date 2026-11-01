package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.entity.CarLoginSession;
import com.ieo.server.domain.user.enums.CarLoginStatus;
import java.time.Instant;

/** 휴대폰 승인 화면에 보여 줄 정보. pollSecret이나 sessionId는 들어가지 않는다. */
public record CarLoginRequestResponse(
		String code,
		String deviceName,
		String approxLocation,
		Instant requestedAt,
		Instant expiresAt,
		CarLoginStatus status) {

	public static CarLoginRequestResponse of(CarLoginSession session, Instant now) {
		return new CarLoginRequestResponse(session.getCode(), session.getDeviceName(), session.getApproxLocation(),
				session.getCreatedAt(), session.getExpiresAt(), session.statusAt(now));
	}
}
