package com.ieo.server.domain.user.service.social;

import com.ieo.server.domain.user.enums.SocialProvider;

/** 공급자별로 "이 토큰이 정말 그 사람인지" 확인한다. 설정(키)이 없으면 isConfigured가 false다. */
public interface SocialIdentityVerifier {

	SocialProvider provider();

	boolean isConfigured();

	/**
	 * @param token       Google은 ID 토큰, 카카오는 인가 코드
	 * @param redirectUri 카카오 인가 코드를 받을 때 쓴 주소. Google은 무시한다
	 * @throws com.ieo.server.global.error.BusinessException AUTH_SOCIAL_PROVIDER_REJECTED 확인 실패
	 */
	SocialProfile verify(String token, String redirectUri);
}
