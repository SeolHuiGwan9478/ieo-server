package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 소셜 인증 확인. token은 Google이면 ID 토큰, 카카오면 인가 코드다.
 * redirectUri는 카카오만 필요하다 (인가 코드를 받을 때 쓴 주소와 같아야 한다).
 */
public record SocialVerifyRequest(
		@NotNull SocialProvider provider,
		@NotBlank @Size(max = 4000) String token,
		@NotBlank @Size(max = 100) String deviceId,
		@Size(max = 500) String redirectUri) {
}
