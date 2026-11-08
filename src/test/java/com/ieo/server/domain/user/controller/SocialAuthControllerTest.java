package com.ieo.server.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.user.entity.SocialIdentity;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.SocialProvider;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.SocialIdentityRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.user.service.social.GoogleIdTokenVerifier;
import com.ieo.server.domain.user.service.social.SocialProfile;
import com.ieo.server.global.sms.SmsSender;
import com.jayway.jsonpath.JsonPath;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 소셜 로그인 흐름. 소셜로 들어와도 번호 인증을 거쳐 가입한다. Google 확인기는 외부(Google 공개키)를 부르므로 가짜로 바꾸고,
 * 카카오는 설정이 없는 상태 그대로 두어 "준비 중" 응답을 확인한다.
 */
// 로컬 .env에 카카오 키가 있어도 미설정 공급자 테스트는 외부 API를 호출하지 않는다.
@SpringBootTest(properties = "ieo.social.kakao.rest-api-key=")
@ActiveProfiles("test")
@Transactional
class SocialAuthControllerTest {

	private static final String DEVICE = "device-web";
	private static final String GOOGLE_SUB = "google-user-1001";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocialIdentityRepository socialIdentityRepository;

	@MockitoBean
	private GoogleIdTokenVerifier googleVerifier;

	@MockitoBean
	private SmsSender smsSender;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		when(googleVerifier.provider()).thenReturn(SocialProvider.GOOGLE);
		when(googleVerifier.isConfigured()).thenReturn(true);
		when(googleVerifier.verify(eq("good-id-token"), any()))
				.thenReturn(new SocialProfile(SocialProvider.GOOGLE, GOOGLE_SUB, "broker@example.com"));
	}

	@Test
	void 처음_오는_구글_계정은_번호_인증까지_마치고_가입한다() throws Exception {
		String response = verifyGoogle()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountExists").value(false))
				.andExpect(jsonPath("$.existingAccount").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String socialToken = JsonPath.read(response, "$.socialToken");
		String phone = "01030000001";
		String phoneToken = JsonPath.read(verifyPhone(phone), "$.phoneToken");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signUpBody("\"phoneToken\": \"" + phoneToken + "\", \"socialToken\": \"" + socialToken + "\"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());

		User user = userRepository.findByPhone(phone).orElseThrow();
		assertThat(user.getName()).isEqualTo("홍길동");
		assertThat(user.getRole()).isEqualTo(UserRole.BROKER);
		SocialIdentity identity = socialIdentityRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_SUB).orElseThrow();
		assertThat(identity.getUser().getId()).isEqualTo(user.getId());
		assertThat(identity.getEmail()).isEqualTo("broker@example.com");
	}

	@Test
	void 번호로_이미_가입한_사람이_소셜로_들어오면_그_계정에_소셜_계정이_붙고_로그인된다() throws Exception {
		String phone = "01030000002";
		User existing = userRepository.save(User.create(phone, UserRole.BROKER));
		existing.changeName("기존 담당자");

		String socialToken = JsonPath.read(verifyGoogle().andReturn().getResponse().getContentAsString(), "$.socialToken");
		String verify = verifyPhone(phone);
		assertThat((Boolean) JsonPath.read(verify, "$.accountExists")).isTrue();
		String phoneToken = JsonPath.read(verify, "$.phoneToken");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signUpBody("\"phoneToken\": \"" + phoneToken + "\", \"socialToken\": \"" + socialToken + "\"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());

		assertThat(userRepository.findAll()).filteredOn(u -> phone.equals(u.getPhone())).hasSize(1);
		SocialIdentity identity = socialIdentityRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_SUB).orElseThrow();
		assertThat(identity.getUser().getId()).isEqualTo(existing.getId());
		assertThat(identity.getUser().getName()).isEqualTo("기존 담당자");
	}

	@Test
	void 소셜_없이_가입된_번호로_다시_가입하면_409다() throws Exception {
		String phone = "01030000003";
		userRepository.save(User.create(phone, UserRole.BROKER));
		String phoneToken = JsonPath.read(verifyPhone(phone), "$.phoneToken");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signUpBody("\"phoneToken\": \"" + phoneToken + "\"")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AUTH_409_PHONE"));
	}

	@Test
	void 가입된_구글_계정은_확인_결과에_계정이_보이고_로그인된다() throws Exception {
		User user = userRepository.save(User.create("01030000011", UserRole.BROKER));
		user.changeName("기존 담당자");
		socialIdentityRepository.save(SocialIdentity.link(user, SocialProvider.GOOGLE, GOOGLE_SUB, null));

		String response = verifyGoogle()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountExists").value(true))
				.andExpect(jsonPath("$.existingAccount.name").value("기존 담당자"))
				.andExpect(jsonPath("$.existingAccount.role").value("BROKER"))
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.socialToken");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/social/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"socialToken\": \"" + token + "\", \"deviceId\": \"" + DEVICE + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
		assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
	}

	@Test
	void socialToken은_한_번만_쓸_수_있고_다른_기기에서는_못_쓴다() throws Exception {
		User user = userRepository.save(User.create("01030000012", UserRole.BROKER));
		socialIdentityRepository.save(SocialIdentity.link(user, SocialProvider.GOOGLE, GOOGLE_SUB, null));
		String token = JsonPath.read(verifyGoogle().andReturn().getResponse().getContentAsString(), "$.socialToken");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/social/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"socialToken\": \"" + token + "\", \"deviceId\": \"other-device\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_SOCIAL"));
	}

	@Test
	void 공급자_확인에_실패하면_401이다() throws Exception {
		when(googleVerifier.verify(eq("bad-id-token"), any()))
				.thenThrow(new com.ieo.server.global.error.BusinessException(com.ieo.server.global.error.ErrorCode.AUTH_SOCIAL_PROVIDER_REJECTED));

		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/social/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\": \"GOOGLE\", \"token\": \"bad-id-token\", \"deviceId\": \"" + DEVICE + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_SOCIAL_PROVIDER"));
	}

	@Test
	void 설정이_없는_공급자는_503이다() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/social/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\": \"KAKAO\", \"token\": \"code\", \"deviceId\": \"" + DEVICE + "\", \"redirectUri\": \"http://localhost:5173/login\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AUTH_503_SOCIAL"));
	}

	@Test
	void 가입_요청에_휴대폰_인증이_없으면_400이다() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signUpBody("\"socialToken\": \"only-social\"")))
				.andExpect(status().isBadRequest());
	}

	private ResultActions verifyGoogle() throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/social/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\": \"GOOGLE\", \"token\": \"good-id-token\", \"deviceId\": \"" + DEVICE + "\"}"));
	}

	private String verifyPhone(String phone) throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/phone/code")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"" + phone + "\"}"))
				.andExpect(status().isNoContent());
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(anyString(), message.capture());
		Matcher matcher = Pattern.compile("\\d{6}").matcher(message.getValue());
		matcher.find();
		return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/phone/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"" + phone + "\",\"code\":\"" + matcher.group() + "\",\"deviceId\":\"" + DEVICE + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private static String signUpBody(String credential) {
		return "{" + credential + ", \"deviceId\": \"" + DEVICE + "\", \"role\": \"BROKER\", \"name\": \"홍길동\","
				+ " \"consents\": [{\"type\": \"TERMS\", \"version\": \"2026-11\", \"agreed\": true},"
				+ " {\"type\": \"PRIVACY\", \"version\": \"2026-11\", \"agreed\": true}]}";
	}
}
