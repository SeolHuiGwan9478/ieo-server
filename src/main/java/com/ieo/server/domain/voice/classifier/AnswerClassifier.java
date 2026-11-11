package com.ieo.server.domain.voice.classifier;

/**
 * 차량 앱이 단어 목록으로 알아듣지 못한 대답을 판별한다 (UX-025 ②).
 * 구현을 바꿔 끼울 수 있게 둔다: 지금은 OpenRouter, 키가 없으면 늘 "모름".
 * 대답 글은 판별에만 쓰고 저장하거나 로그에 남기지 않는다.
 */
public interface AnswerClassifier {

	/**
	 * @param question 앱이 물은 말 (예: "대구 성서 하차지에 도착하셨어요?")
	 * @param answer   음성 인식으로 받은 차주 대답
	 * @return 실패하거나 시간이 넘으면 예외 없이 [Classification.unknown]
	 */
	Classification classify(String question, String answer);
}
