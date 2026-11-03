package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 담당자 초대. 번호는 숫자만 (예: 01012345678) */
public record InviteMemberRequest(
		@NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호는 숫자만 입력해 주세요.") String phone) {
}
