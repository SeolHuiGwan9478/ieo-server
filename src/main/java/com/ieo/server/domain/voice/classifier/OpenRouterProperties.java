package com.ieo.server.domain.voice.classifier;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenRouter로 대답 판별 (application.yml의 ieo.voice.openrouter).
 *
 * @param apiKey  OpenRouter API 키. 환경변수 OPENROUTER_API_KEY. 비어 있으면 부르지 않는다
 * @param model   OpenRouter 모델 이름. 환경변수 OPENROUTER_MODEL. 비어 있으면 부르지 않는다.
 *                TypeSafe Jev(typesafe/jev-1.14, ~typesafe/jev-latest)면 결정 API로 부른다
 * @param baseUrl OpenRouter API 주소
 * @param timeout 이 안에 답이 없으면 "모름". 차량 앱은 15초 안에 다시 물어야 하므로 짧게 둔다
 */
@ConfigurationProperties("ieo.voice.openrouter")
public record OpenRouterProperties(
		String apiKey,
		String model,
		@DefaultValue("https://openrouter.ai/api/v1") String baseUrl,
		@DefaultValue("2s") Duration timeout) {

	/** TypeSafe Jev: 채팅이 아니라 결정 API(/api/alpha/decisions)로 부른다 */
	public boolean isJev() {
		return model != null && model.replace("~", "").startsWith("typesafe/");
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
	}
}
