package com.ieo.server.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class UserControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		userRepository.deleteAll();
	}


	@Test
	void 없는_사용자면_404() throws Exception {
		mockMvc.perform(get("/api/users/me").requestAttr("userId", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_404"));
	}

	@Test
	void 내_정보_조회와_수정() throws Exception {
		User user = userRepository.save(User.createDriver("01012345678"));

		mockMvc.perform(get("/api/users/me").requestAttr("userId", user.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("01012345678"))
				.andExpect(jsonPath("$.role").value("DRIVER"))
				.andExpect(jsonPath("$.consentLocation").value(false));

		mockMvc.perform(patch("/api/users/me").requestAttr("userId", user.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "설기사", "consentLocation": true, "consentBrokerShare": false, "consentVoice": true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("설기사"))
				.andExpect(jsonPath("$.consentLocation").value(true))
				.andExpect(jsonPath("$.consentVoice").value(true));
	}

	@Test
	void 동의_항목이_빠지면_400() throws Exception {
		User user = userRepository.save(User.createDriver("01012345678"));

		mockMvc.perform(patch("/api/users/me").requestAttr("userId", user.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "설기사"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(3));
	}

	@Test
	void 인증번호_엔드포인트는_아직_501() throws Exception {
		mockMvc.perform(post("/api/auth/code")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phone": "01012345678"}
								"""))
				.andExpect(status().isNotImplemented())
				.andExpect(jsonPath("$.code").value("COMMON_501"));
	}

	@Test
	void 휴대폰_번호_형식이_틀리면_400() throws Exception {
		mockMvc.perform(post("/api/auth/code")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phone": "010-1234-5678"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));
	}
}
