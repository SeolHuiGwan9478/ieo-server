package com.ieo.server.domain.voice.classifier;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenRouter의 TypeSafe Jev(분류 모델)로 예/아니오/모름을 고른다 (POST /api/alpha/decisions).
 * 글을 생성하지 않고 정해 둔 선택지 중 하나와 확신을 돌려주므로 빠르고 싸다 (출력 토큰은 무료).
 * 요청: state(질문·대답) + questions.meaning(type choice, criteria YES/NO/UNKNOWN).
 * 응답: answers.meaning.choice, answers.meaning.confidence. 거절(refusal)이나 실패면 "모름".
 * 대답 글과 키가 로그에 남지 않게, 실패하면 예외 종류만 남긴다.
 */
@Slf4j
public class OpenRouterJevAnswerClassifier implements AnswerClassifier {

	static final String DECISIONS_PATH = "/alpha/decisions";
	private static final String QUESTION = "meaning";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Map<String, Object> QUESTIONS = Map.of(QUESTION, Map.of(
			"type", "choice",
			"criteria", Map.of(
					"YES", "기사의 대답이 앱의 질문에 '예'라는 뜻이다 (맞다, 도착했다, 그렇게 해 달라)",
					"NO", "기사의 대답이 '아니오'·'아직'·'나중에'라는 뜻이다",
					"UNKNOWN", "질문과 관계없는 말이거나 뜻이 분명하지 않다")));

	private final OpenRouterProperties properties;
	private final RestClient client;

	public OpenRouterJevAnswerClassifier(OpenRouterProperties properties) {
		this(properties, RestClient.builder().requestFactory(requestFactory(properties.timeout())));
	}

	/** 테스트가 MockRestServiceServer를 묶은 builder를 넘긴다 */
	OpenRouterJevAnswerClassifier(OpenRouterProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.client = builder.baseUrl(apiRoot(properties.baseUrl())).build();
	}

	/** https://openrouter.ai/api/v1 → https://openrouter.ai/api (결정 API는 /api/alpha 아래에 있다) */
	static String apiRoot(String baseUrl) {
		String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : trimmed;
	}

	private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(1));
		factory.setReadTimeout(timeout);
		return factory;
	}

	@Override
	public Classification classify(String question, String answer) {
		String request = JSON.writeValueAsString(Map.of(
				"model", properties.model(),
				"state", Map.of(
						"situation", "화물차 앱이 기사에게 예/아니오로 물었고, 기사가 말로 대답했다. 대답은 음성 인식 결과라 글자가 틀릴 수 있다",
						"question", question,
						"answer", answer),
				"questions", QUESTIONS));
		try {
			String body = client.post()
					.uri(DECISIONS_PATH)
					.header("Authorization", "Bearer " + properties.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(String.class);
			return parse(body);
		} catch (RuntimeException e) {
			log.warn("Jev 대답 판별 실패: {}", e.getClass().getSimpleName());
			return Classification.unknown();
		}
	}

	static Classification parse(String body) {
		JsonNode meaning = JSON.readTree(body == null ? "{}" : body).path("answers").path(QUESTION);
		if (!"choice".equals(meaning.path("type").asString())) {
			// 거절(refusal)이거나 모양이 다르다
			return Classification.unknown();
		}
		YesNo answer;
		try {
			answer = YesNo.valueOf(meaning.path("choice").asString());
		} catch (IllegalArgumentException e) {
			return Classification.unknown();
		}
		return new Classification(answer, Math.clamp(meaning.path("confidence").asDouble(0), 0, 1));
	}
}
