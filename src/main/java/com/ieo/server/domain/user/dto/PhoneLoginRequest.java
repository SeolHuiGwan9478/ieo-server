package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 번호는 받지 않는다. 어떤 번호인지는 phoneToken으로 서버가 안다. */
public record PhoneLoginRequest(@NotBlank String phoneToken, @NotBlank @Size(max = 100) String deviceId) {
}
