package com.ieo.server.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CarLoginControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String phoneBearer;
	private Long userId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090000201", UserRole.DRIVER));
		userId = user.getId();
		phoneBearer = "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	@Test
	void 차량이_QR을_띄우고_휴대폰이_승인하면_차량이_토큰을_한_번_받는다() throws Exception {
		String session = createSession();
		String sessionId = JsonPath.read(session, "$.sessionId");
		String code = JsonPath.read(session, "$.code");
		String pollSecret = JsonPath.read(session, "$.pollSecret");
		assertThat(code).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
		assertThat((String) JsonPath.read(session, "$.qrUrl")).isEqualTo("ieo://car-login?code=" + code);
		assertThat((String) JsonPath.read(session, "$.qrUrl")).doesNotContain(pollSecret);

		// 승인 전: 기다림
		poll(sessionId, pollSecret)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.tokens").doesNotExist());

		// 휴대폰: 요청 정보 보고 승인
		mockMvc.perform(get("/api/car-login/requests/" + code).header("Authorization", phoneBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deviceName").value("Pleos 차량 앱"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.pollSecret").doesNotExist());
		mockMvc.perform(post("/api/car-login/requests/" + code + "/approve").header("Authorization", phoneBearer))
				.andExpect(status().isNoContent());

		// 차량: 토큰 받기. 승인한 사용자의 토큰이다
		String tokens = poll(sessionId, pollSecret)
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(tokens, "$.tokens.accessToken");
		assertThat(jwtProvider.parseAccessToken(accessToken).userId()).isEqualTo(userId);
		assertThat(userRepository.findById(userId).orElseThrow().getLastLoginAt()).isNotNull();

		// 다시 물어도 토큰은 다시 안 준다
		poll(sessionId, pollSecret)
				.andExpect(jsonPath("$.status").value("CONSUMED"))
				.andExpect(jsonPath("$.tokens").doesNotExist());
	}

	@Test
	void pollSecret이_틀리면_세션이_없는_것처럼_404() throws Exception {
		String session = createSession();
		String sessionId = JsonPath.read(session, "$.sessionId");

		poll(sessionId, "wrong-secret")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAR_LOGIN_404"));
		mockMvc.perform(post("/api/auth/car-login/sessions/" + sessionId + "/poll"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 휴대폰이_거절하면_차량은_거절됨을_받는다() throws Exception {
		String session = createSession();
		String code = JsonPath.read(session, "$.code");

		mockMvc.perform(post("/api/car-login/requests/" + code + "/reject").header("Authorization", phoneBearer))
				.andExpect(status().isNoContent());

		poll(JsonPath.read(session, "$.sessionId"), JsonPath.read(session, "$.pollSecret"))
				.andExpect(jsonPath("$.status").value("REJECTED"));
		mockMvc.perform(post("/api/car-login/requests/" + code + "/approve").header("Authorization", phoneBearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CAR_LOGIN_409"));
	}

	@Test
	void 승인은_로그인한_휴대폰만_할_수_있다() throws Exception {
		String code = JsonPath.read(createSession(), "$.code");

		mockMvc.perform(post("/api/car-login/requests/" + code + "/approve"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/car-login/requests/" + code))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 코드를_소문자나_하이픈_없이_쳐도_찾는다() throws Exception {
		String code = JsonPath.read(createSession(), "$.code");
		String typed = code.replace("-", "").toLowerCase();

		mockMvc.perform(get("/api/car-login/requests/" + typed).header("Authorization", phoneBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void 없는_코드면_404() throws Exception {
		mockMvc.perform(get("/api/car-login/requests/AAAA-AAAA").header("Authorization", phoneBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAR_LOGIN_404"));
	}

	private String createSession() throws Exception {
		return mockMvc.perform(post("/api/auth/car-login/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"deviceId": "car-device-1", "deviceName": "Pleos 차량 앱", "appVersion": "0.3.0"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions poll(String sessionId, String pollSecret) throws Exception {
		return mockMvc.perform(post("/api/auth/car-login/sessions/" + sessionId + "/poll")
				.header(CarLoginController.POLL_SECRET_HEADER, pollSecret));
	}
}
