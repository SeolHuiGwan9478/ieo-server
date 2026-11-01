package com.ieo.server.global.util;

import java.security.SecureRandom;
import java.util.Base64;

/** 맞힐 수 없는 랜덤 문자열 (phoneToken, pollSecret 등). */
public final class RandomTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RandomTokens() {
	}

	/** 32바이트 랜덤 값을 URL에 넣어도 되는 Base64로 */
	public static String urlSafe() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
