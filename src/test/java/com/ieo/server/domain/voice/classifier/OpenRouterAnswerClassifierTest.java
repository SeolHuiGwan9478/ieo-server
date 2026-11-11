package com.ieo.server.domain.voice.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** OpenRouter 호출과 응답 해석. 실제 OpenRouter 대신 MockRestServiceServer가 응답한다 */
class OpenRouterAnswerClassifierTest {

	private static final String URL = "https://openrouter.ai/api/v1/chat/completions";

	private MockRestServiceServer server;
	private OpenRouterAnswerClassifier classifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		OpenRouterProperties properties = new OpenRouterProperties("test-key", "test/model", "https://openrouter.ai/api/v1", Duration.ofSeconds(2));
		classifier = new OpenRouterAnswerClassifier(properties, builder);
	}

	private static String reply(String content) {
		return """
				{"choices":[{"message":{"role":"assistant","content":%s}}]}
				""".formatted(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(content));
	}

	@Test
	void 질문과_대답을_보내고_고른_답과_확신을_읽는다() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("test/model"))
				.andExpect(jsonPath("$.messages[1].content").value("질문: 도착하셨어요?\n대답: 어어 들어왔지"))
				.andExpect(jsonPath("$.response_format.json_schema.schema.properties.answer.enum[0]").value("YES"))
				.andRespond(withSuccess(reply("{\"answer\":\"YES\",\"confidence\":0.93}"), MediaType.APPLICATION_JSON));

		Classification result = classifier.classify("도착하셨어요?", "어어 들어왔지");

		assertEquals(YesNo.YES, result.answer());
		assertEquals(0.93, result.confidence(), 1e-9);
		server.verify();
	}

	@Test
	void 코드_블록으로_감싸_와도_읽는다() {
		server.expect(requestTo(URL))
				.andRespond(withSuccess(reply("```json\n{\"answer\":\"NO\",\"confidence\":0.8}\n```"), MediaType.APPLICATION_JSON));

		assertEquals(YesNo.NO, classifier.classify("도착하셨어요?", "아직 줄 서 있어").answer());
	}

	@Test
	void 서버_오류면_모름() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertEquals(Classification.unknown(), classifier.classify("도착하셨어요?", "응"));
	}

	@Test
	void 셋_중_하나가_아니거나_모양이_다르면_모름() {
		assertEquals(Classification.unknown(), OpenRouterAnswerClassifier.parse(reply("{\"answer\":\"MAYBE\",\"confidence\":1}")));
		assertEquals(Classification.unknown(), OpenRouterAnswerClassifier.parse(reply("네 맞아요")));
		assertEquals(Classification.unknown(), OpenRouterAnswerClassifier.parse("{}"));
	}

	@Test
	void 확신은_0에서_1_사이로_맞춘다() {
		assertEquals(1.0, OpenRouterAnswerClassifier.parse(reply("{\"answer\":\"YES\",\"confidence\":7}")).confidence(), 1e-9);
	}
}
