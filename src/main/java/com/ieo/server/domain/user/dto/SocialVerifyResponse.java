package com.ieo.server.domain.user.dto;

import java.time.Instant;

/**
 * 소셜 인증 확인 결과. PhoneVerifyResponse와 같은 흐름이다.
 * socialToken은 계정 유무와 상관없이 발급하고, 로그인(/social/login)이나 가입(/signup)에 한 번 쓴다.
 */
public record SocialVerifyResponse(
		String socialToken,
		Instant socialTokenExpiresAt,
		boolean accountExists,
		ExistingAccountResponse existingAccount) {
}
