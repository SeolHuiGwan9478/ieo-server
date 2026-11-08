package com.ieo.server.domain.user.service.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 소셜 로그인 설정 (application.yml의 ieo.social). 키가 비어 있으면 그 공급자는 꺼진 것으로 본다.
 *
 * @param google clientId: Google Cloud 콘솔의 OAuth 클라이언트 ID. 콘솔(ieo-web)의 VITE_GOOGLE_CLIENT_ID와 같은 값
 * @param kakao  restApiKey: 카카오 디벨로퍼스의 REST API 키. clientSecret은 "보안 > Client Secret"을 켰을 때만
 */
@ConfigurationProperties("ieo.social")
public record SocialProperties(Google google, Kakao kakao) {

	public record Google(
			String clientId,
			@DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwksUrl) {
	}

	public record Kakao(
			String restApiKey,
			String clientSecret,
			@DefaultValue("https://kauth.kakao.com") String authBaseUrl,
			@DefaultValue("https://kapi.kakao.com") String apiBaseUrl) {
	}
}
