package com.ieo.server.domain.user.dto;

import java.time.Instant;

/**
 * 인증번호 확인 결과. 가입된 계정이 있으면 existingAccount를 채우고, 앱은 "이미 가입된 계정이 있어요" 모달을 띄운다.
 * phoneToken은 계정 유무와 상관없이 발급하고, 로그인(/phone/login)이나 가입(/signup)에 한 번 쓴다.
 */
public record PhoneVerifyResponse(
		String phoneToken,
		Instant phoneTokenExpiresAt,
		boolean accountExists,
		ExistingAccountResponse existingAccount) {
}
