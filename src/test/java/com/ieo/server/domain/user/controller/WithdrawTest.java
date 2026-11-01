package com.ieo.server.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.entity.PhoneVerification;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.PhoneVerificationRepository;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 탈퇴 (ACC-004) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WithdrawTest {

	private static final String PHONE = "01090000301";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private UserConsentRepository userConsentRepository;

	@Autowired
	private PhoneVerificationRepository phoneVerificationRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;
	private User user;
	private TokenResponse tokens;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		user = User.create(PHONE, UserRole.DRIVER);
		user.changeName("김도윤");
		userRepository.save(user);
		Driver driver = Driver.of(user);
		driver.changeLicenseNumber("12-345678");
		driverRepository.save(driver);
		userConsentRepository.save(UserConsent.of(user, ConsentType.TERMS, "2026-10-01", true));
		phoneVerificationRepository.save(PhoneVerification.create(PHONE));
		tokens = jwtProvider.issue(user.getId());
	}

	@Test
	void 탈퇴하면_개인정보를_지우고_같은_번호로_다시_가입할_수_있다() throws Exception {
		mockMvc.perform(post("/api/users/me/withdraw")
						.header("Authorization", "Bearer " + tokens.accessToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\": \"다른 앱을 써요\"}"))
				.andExpect(status().isNoContent());
		entityManager.flush();
		entityManager.clear();

		User withdrawn = userRepository.findById(user.getId()).orElseThrow();
		assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(withdrawn.getPhone()).isNull();
		assertThat(withdrawn.getName()).isNull();
		assertThat(withdrawn.getStatusReason()).isEqualTo("다른 앱을 써요");
		assertThat(driverRepository.findById(user.getId()).orElseThrow().getLicenseNumber()).isNull();
		assertThat(phoneVerificationRepository.findByPhoneForUpdate(PHONE)).isEmpty();
		assertThat(userRepository.existsByPhone(PHONE)).isFalse();

		// 동의 이력은 증빙으로 남긴다 (번호·이름 없이 계정 ID에만 묶여 있다)
		assertThat(userConsentRepository.findLatestByUserId(user.getId())).hasSize(1);
	}

	@Test
	void 탈퇴한_뒤에는_남은_토큰으로도_쓸_수_없다() throws Exception {
		mockMvc.perform(post("/api/users/me/withdraw").header("Authorization", "Bearer " + tokens.accessToken()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokens.accessToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_404"));
		mockMvc.perform(post("/api/users/me/withdraw").header("Authorization", "Bearer " + tokens.accessToken()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"" + tokens.refreshToken() + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("USER_403"));
	}

	@Test
	void 사유가_너무_길면_400() throws Exception {
		mockMvc.perform(post("/api/users/me/withdraw")
						.header("Authorization", "Bearer " + tokens.accessToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\": \"" + "가".repeat(201) + "\"}"))
				.andExpect(status().isBadRequest());
	}
}
