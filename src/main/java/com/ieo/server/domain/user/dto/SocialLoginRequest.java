package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 어느 소셜 계정인지는 socialToken으로 서버가 안다. */
public record SocialLoginRequest(@NotBlank String socialToken, @NotBlank @Size(max = 100) String deviceId) {
}
