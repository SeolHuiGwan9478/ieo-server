package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 차량 앱이 QR 화면을 띄울 때 보낸다. 로그인 전이라 기기 정보만 있다. */
public record CreateCarLoginSessionRequest(
		@NotBlank @Size(max = 100) String deviceId,
		@Size(max = 50) String deviceName,
		@Size(max = 20) String appVersion) {
}
