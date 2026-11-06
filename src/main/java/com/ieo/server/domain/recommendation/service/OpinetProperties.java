package com.ieo.server.domain.recommendation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 오피넷 유가정보 API (application.yml의 ieo.opinet).
 *
 * @param apiKey  공사에서 받은 키. 환경변수 OPINET_API_KEY로 넣는다. 비어 있으면 부르지 않는다
 * @param baseUrl 오피넷 주소
 */
@ConfigurationProperties("ieo.opinet")
public record OpinetProperties(
		String apiKey,
		@DefaultValue("https://www.opinet.co.kr") String baseUrl) {
}
