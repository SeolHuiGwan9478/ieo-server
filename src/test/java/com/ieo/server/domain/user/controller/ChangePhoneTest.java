package com.ieo.server.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 내 정보 수정에서 휴대폰 번호 바꾸기 (ACC-008): 새 번호로 인증한 phoneToken으로만 바꾼다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChangePhoneTest {

	private static final String DEVICE = "device-a";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private SmsSender smsSender;

	private MockMvc mockMvc;
	private Long userId;
	private String bearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		userId = userRepository.save(User.create("01030000001", UserRole.DRIVER)).getId();
		bearer = "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	@Test
	void 새_번호로_인증하면_번호가_바뀐다() throws Exception {
		changePhone(phoneToken("01030000002"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value("01030000002"));
	}

	@Test
	void 다른_계정이_쓰는_번호로는_바꿀_수_없다() throws Exception {
		userRepository.save(User.create("01030000003", UserRole.DRIVER));
		changePhone(phoneToken("01030000003"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AUTH_409_PHONE"));
	}

	@Test
	void 인증하지_않은_토큰이면_401() throws Exception {
		changePhone("not-a-token").andExpect(status().isUnauthorized());
	}

	private ResultActions changePhone(String phoneToken) throws Exception {
		return mockMvc.perform(post("/api/users/me/phone").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"phoneToken\":\"" + phoneToken + "\",\"deviceId\":\"" + DEVICE + "\"}"));
	}

	private String phoneToken(String phone) throws Exception {
		mockMvc.perform(post("/api/auth/phone/code").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
				.andExpect(status().isNoContent());
		String body = mockMvc.perform(post("/api/auth/phone/verify").contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"" + phone + "\",\"code\":\"" + lastCode() + "\",\"deviceId\":\"" + DEVICE + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.phoneToken");
	}

	private String lastCode() {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(anyString(), message.capture());
		Matcher matcher = Pattern.compile("\\d{6}").matcher(message.getValue());
		matcher.find();
		return matcher.group();
	}
}
