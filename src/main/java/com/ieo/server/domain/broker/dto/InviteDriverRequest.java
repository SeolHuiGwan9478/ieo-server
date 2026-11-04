package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 단골 차주 연결 요청. 하이픈 없는 휴대폰 번호 */
public record InviteDriverRequest(
		@NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호를 숫자만 입력해 주세요.") String phone) {
}
