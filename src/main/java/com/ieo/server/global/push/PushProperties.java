package com.ieo.server.global.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 휴대폰 푸시 (application.yml의 ieo.push).
 *
 * @param firebaseCredentials Firebase 서비스 계정 키. JSON 내용 그대로나 파일 경로. 환경변수 FIREBASE_CREDENTIALS로 넣는다.
 *                            비밀이라 커밋하지 않는다. 비어 있으면 푸시를 보내지 않는다
 */
@ConfigurationProperties("ieo.push")
public record PushProperties(String firebaseCredentials) {

	public boolean enabled() {
		return firebaseCredentials != null && !firebaseCredentials.isBlank();
	}
}
