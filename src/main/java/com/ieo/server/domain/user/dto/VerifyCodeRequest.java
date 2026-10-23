package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** deviceId는 앱 설치마다 만드는 값. 발급한 phoneToken을 이 기기에서만 쓸 수 있게 묶는다. */
public record VerifyCodeRequest(
		@NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호는 숫자만 입력해 주세요.") String phone,
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다.") String code,
		@NotBlank @Size(max = 100) String deviceId) {
}
