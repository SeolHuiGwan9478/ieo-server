package com.ieo.server.domain.voice.classifier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 키와 모델이 있으면 OpenRouter, 없으면 늘 "모름" (앱은 단어 목록만으로 판별하고 못 알아들으면 다시 묻는다) */
@Slf4j
@Configuration
public class AnswerClassifierConfig {

	@Bean
	public AnswerClassifier answerClassifier(OpenRouterProperties properties) {
		if (properties.isConfigured()) {
			return new OpenRouterAnswerClassifier(properties);
		}
		log.info("OpenRouter 키나 모델이 없어 음성 대답을 서버에서 판별하지 않습니다 (OPENROUTER_API_KEY, OPENROUTER_MODEL)");
		return (question, answer) -> Classification.unknown();
	}
}
