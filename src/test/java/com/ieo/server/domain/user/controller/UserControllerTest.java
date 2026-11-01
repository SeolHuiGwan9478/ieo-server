package com.ieo.server.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
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

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		userRepository.deleteAll();
	}


	@Test
	void 없는_사용자면_404() throws Exception {
		mockMvc.perform(get("/api/users/me").header("Authorization", bearer(999L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_404"));
	}

	@Test
	void 내_정보_조회와_수정() throws Exception {
		User user = userRepository.save(User.create("01012345678", UserRole.DRIVER));

		mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("01012345678"))
				.andExpect(jsonPath("$.role").value("DRIVER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "설기사"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("설기사"));
	}

	@Test
	void 이름이_30자를_넘으면_400() throws Exception {
		User user = userRepository.save(User.create("01012345678", UserRole.DRIVER));

		mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s"}
								""".formatted("가".repeat(31))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void 토큰이_없으면_401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("COMMON_401"));
	}

	@Test
	void 리프레시_토큰으로는_API를_부를_수_없다() throws Exception {
		User user = userRepository.save(User.create("01012345678", UserRole.DRIVER));

		mockMvc.perform(get("/api/users/me")
						.header("Authorization", "Bearer " + jwtProvider.issue(user.getId()).refreshToken()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_TOKEN"));
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}
}
