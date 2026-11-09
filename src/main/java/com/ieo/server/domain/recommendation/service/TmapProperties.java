package com.ieo.server.domain.recommendation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TMAP 화물차 경로안내 (application.yml의 ieo.tmap).
 *
 * @param appKey  SK Open API 앱 키. 환경변수 TMAP_APP_KEY로 넣는다. 비어 있으면 호출하지 않고 어림값을 쓴다
 * @param baseUrl TMAP API 주소
 */
@ConfigurationProperties("ieo.tmap")
public record TmapProperties(
		String appKey,
		@DefaultValue("https://apis.openapi.sk.com") String baseUrl) {
}
