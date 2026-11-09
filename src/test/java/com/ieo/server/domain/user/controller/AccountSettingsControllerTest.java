package com.ieo.server.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
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
class AccountSettingsControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String phone;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		phone = bearerOfNewDriver("01090008801");
	}

	@Test
	void 차량이_로그인하면_목록에_나오고_끊으면_토큰이_바로_막힌다() throws Exception {
		String tokens = loginCar("car-1", "내 트럭");
		String carBearer = "Bearer " + JsonPath.read(tokens, "$.tokens.accessToken");
		String refresh = JsonPath.read(tokens, "$.tokens.refreshToken");

		String list = get(phone, "/api/users/me/car-sessions")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].deviceName").value("내 트럭"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(list, "$[0].id");
		get(carBearer, "/api/users/me").andExpect(status().isOk());
		refresh(refresh).andExpect(status().isOk());

		mockMvc.perform(post("/api/users/me/car-sessions/" + id + "/revoke").header("Authorization", phone))
				.andExpect(status().isNoContent());

		get(carBearer, "/api/users/me").andExpect(status().isUnauthorized());
		refresh(refresh).andExpect(status().isUnauthorized());
		get(phone, "/api/users/me").andExpect(status().isOk());
		get(phone, "/api/users/me/car-sessions").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 같은_차에서_다시_로그인하면_한_대로_보이고_전_토큰은_막힌다() throws Exception {
		String first = "Bearer " + JsonPath.read(loginCar("car-1", "내 트럭"), "$.tokens.accessToken");
		loginCar("car-1", "내 트럭");

		get(phone, "/api/users/me/car-sessions").andExpect(jsonPath("$.length()").value(1));
		get(first, "/api/users/me").andExpect(status().isUnauthorized());
	}

	@Test
	void 차량이_스스로_로그아웃하면_목록에서_빠지고_휴대폰_토큰으로는_아무_일도_없다() throws Exception {
		String car = "Bearer " + JsonPath.read(loginCar("car-1", "내 트럭"), "$.tokens.accessToken");

		mockMvc.perform(post("/api/users/me/car-sessions/current/sign-out").header("Authorization", phone))
				.andExpect(status().isNoContent());
		get(phone, "/api/users/me/car-sessions").andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(post("/api/users/me/car-sessions/current/sign-out").header("Authorization", car))
				.andExpect(status().isNoContent());
		get(phone, "/api/users/me/car-sessions").andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 다른_사람의_차량은_끊을_수_없다() throws Exception {
		loginCar("car-1", "내 트럭");
		String id = JsonPath.read(get(phone, "/api/users/me/car-sessions").andReturn().getResponse().getContentAsString(), "$[0].id");
		String other = bearerOfNewDriver("01090008802");

		mockMvc.perform(post("/api/users/me/car-sessions/" + id + "/revoke").header("Authorization", other))
				.andExpect(status().isNotFound());
		get(phone, "/api/users/me/car-sessions").andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 선택_동의는_바꿀_수_있고_필수_동의는_철회할_수_없다() throws Exception {
		get(phone, "/api/users/me/consents")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.type=='TERMS')].required").value(true))
				.andExpect(jsonPath("$[?(@.type=='MARKETING')].required").value(false))
				.andExpect(jsonPath("$[?(@.type=='MARKETING')].agreed").value(false));

		putJson("/api/users/me/consents/MARKETING", "{\"agreed\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.agreed").value(true))
				.andExpect(jsonPath("$.version").value("2026-11"));
		get(phone, "/api/users/me/consents").andExpect(jsonPath("$[?(@.type=='MARKETING')].agreed").value(true));

		putJson("/api/users/me/consents/MARKETING", "{\"agreed\":false}")
				.andExpect(jsonPath("$.agreed").value(false));
		putJson("/api/users/me/consents/TERMS", "{\"agreed\":false}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("USER_400_CONSENT"));
	}

	@Test
	void 사업자_정보를_저장하고_지운다() throws Exception {
		get(phone, "/api/users/me/business").andExpect(jsonPath("$.businessNumber").doesNotExist());

		putJson("/api/users/me/business", "{\"businessName\":\"한길운수\",\"businessNumber\":\"123-45-67891\",\"representativeName\":\"홍길동\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.businessNumber").value("1234567891"));
		putJson("/api/users/me/business", "{\"businessName\":\"한길운수\",\"businessNumber\":\"1234567890\",\"representativeName\":\"홍길동\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BROKER_400_BUSINESS_NUMBER"));
		get(phone, "/api/users/me/business").andExpect(jsonPath("$.businessName").value("한길운수"));

		mockMvc.perform(delete("/api/users/me/business").header("Authorization", phone)).andExpect(status().isNoContent());
		get(phone, "/api/users/me/business").andExpect(jsonPath("$.businessName").doesNotExist());
	}

	private String loginCar(String deviceId, String deviceName) throws Exception {
		String session = mockMvc.perform(post("/api/auth/car-login/sessions").contentType(MediaType.APPLICATION_JSON)
						.content("{\"deviceId\":\"" + deviceId + "\",\"deviceName\":\"" + deviceName + "\",\"appVersion\":\"1.0\"}"))
				.andExpect(status().is2xxSuccessful())
				.andReturn().getResponse().getContentAsString();
		String code = JsonPath.read(session, "$.code");
		mockMvc.perform(post("/api/car-login/requests/" + code + "/approve").header("Authorization", phone))
				.andExpect(status().is2xxSuccessful());
		return mockMvc.perform(post("/api/auth/car-login/sessions/" + JsonPath.read(session, "$.sessionId") + "/poll")
						.header("X-Poll-Secret", (String) JsonPath.read(session, "$.pollSecret")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions get(String bearer, String path) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", bearer));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions putJson(String path, String body) throws Exception {
		return mockMvc.perform(put(path).header("Authorization", phone).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String bearerOfNewDriver(String number) {
		User user = userRepository.save(User.create(number, UserRole.DRIVER));
		driverRepository.save(Driver.of(user));
		return "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}
}
