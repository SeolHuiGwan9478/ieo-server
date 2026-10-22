package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendCodeRequest(
		@NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호는 숫자만 입력해 주세요.") String phone) {
}
