package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 번호는 받지 않는다. 어떤 번호인지는 phoneToken으로 서버가 안다. carDeviceName은 차량 앱에서 로그인할 때만 보낸다 */
public record PhoneLoginRequest(
		@NotBlank String phoneToken,
		@NotBlank @Size(max = 100) String deviceId,
		@Size(max = 50) String carDeviceName,
		@Size(max = 20) String appVersion) {

	public PhoneLoginRequest(String phoneToken, String deviceId) {
		this(phoneToken, deviceId, null, null);
	}
}
