package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 휴대폰 번호 바꾸기 (ACC-008). 새 번호로 받은 인증번호를 확인하고 받은 phoneToken을 보낸다 */
public record ChangePhoneRequest(@NotBlank String phoneToken, @NotBlank @Size(max = 100) String deviceId) {
}
