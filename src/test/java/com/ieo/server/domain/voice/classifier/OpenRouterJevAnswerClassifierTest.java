package com.ieo.server.domain.voice.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** OpenRouter Jev 결정 API 호출과 응답 해석. 실제 OpenRouter 대신 MockRestServiceServer가 응답한다 */
class OpenRouterJevAnswerClassifierTest {

	private static final String URL = "https://openrouter.ai/api/alpha/decisions";

	private MockRestServiceServer server;
	private OpenRouterJevAnswerClassifier classifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		OpenRouterProperties properties = new OpenRouterProperties("test-key", "typesafe/jev-1.14", "https://openrouter.ai/api/v1", Duration.ofSeconds(2));
		classifier = new OpenRouterJevAnswerClassifier(properties, builder);
	}

	@Test
	void 질문과_대답을_상태로_보내고_고른_답과_확신을_읽는다() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("typesafe/jev-1.14"))
				.andExpect(jsonPath("$.state.question").value("도착하셨어요?"))
				.andExpect(jsonPath("$.state.answer").value("어어 들어왔지"))
				.andExpect(jsonPath("$.questions.meaning.type").value("choice"))
				.andExpect(jsonPath("$.questions.meaning.criteria.YES").exists())
				.andRespond(withSuccess("""
						{"id":"gen-dec-1","model":"typesafe/jev-1.14-20261101","answers":{"meaning":{"type":"choice","choice":"YES",
						 "confidence":0.91,"probabilities":{"YES":0.95,"NO":0.03,"UNKNOWN":0.02}}},"usage":{"input_tokens":180,"output_tokens":20}}
						""", MediaType.APPLICATION_JSON));

		Classification result = classifier.classify("도착하셨어요?", "어어 들어왔지");

		assertEquals(YesNo.YES, result.answer());
		assertEquals(0.91, result.confidence(), 1e-9);
		server.verify();
	}

	@Test
	void 거절이나_실패면_모름() {
		assertEquals(Classification.unknown(), OpenRouterJevAnswerClassifier.parse("""
				{"answers":{"meaning":{"type":"refusal"}}}
				"""));
		server.expect(requestTo(URL)).andRespond(withServerError());
		assertEquals(Classification.unknown(), classifier.classify("도착하셨어요?", "응"));
	}

	@Test
	void 모델_이름으로_Jev를_알아본다() {
		assertTrue(new OpenRouterProperties("k", "~typesafe/jev-latest", "u", Duration.ofSeconds(2)).isJev());
		assertTrue(!new OpenRouterProperties("k", "anthropic/claude-haiku-5.5", "u", Duration.ofSeconds(2)).isJev());
		assertEquals("https://openrouter.ai/api", OpenRouterJevAnswerClassifier.apiRoot("https://openrouter.ai/api/v1/"));
	}
}
