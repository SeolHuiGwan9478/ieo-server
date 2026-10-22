package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyCodeRequest(
		@NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호는 숫자만 입력해 주세요.") String phone,
		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다.") String code) {
}
