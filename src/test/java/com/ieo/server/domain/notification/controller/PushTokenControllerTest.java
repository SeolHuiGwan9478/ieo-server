package com.ieo.server.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.notification.dto.NotificationResponse;
import com.ieo.server.domain.notification.enums.NotificationType;
import com.ieo.server.domain.notification.repository.PushTokenRepository;
import com.ieo.server.domain.notification.service.PushTokenService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.push.RecordingPushSender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 휴대폰 푸시 기기 등록과 보내기 (09 알림, FCM).
 * 보내기는 알림 저장이 끝난 뒤 새 트랜잭션에서 토큰을 읽으므로, 이 테스트는 트랜잭션으로 감싸지 않고 끝에 지운다
 */
@SpringBootTest
@ActiveProfiles("test")
class PushTokenControllerTest {

	@Autowired
	private RecordingPushSender pushSender;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PushTokenRepository tokenRepository;

	@Autowired
	private PushTokenService pushTokenService;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private User kim;
	private User park;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		kim = userRepository.save(User.create("01090007001", UserRole.DRIVER));
		park = userRepository.save(User.create("01090007002", UserRole.DRIVER));
		pushSender.clear();
	}

	@AfterEach
	void tearDown() {
		pushTokenService.removeAll(kim.getId());
		pushTokenService.removeAll(park.getId());
		userRepository.deleteAll(List.of(kim, park));
		pushSender.clear();
	}

	@Test
	void 토큰을_올리면_알림이_그_휴대폰으로_가고_화면을_정할_data를_담는다() throws Exception {
		token(kim, "token-a").andExpect(status().isNoContent());
		token(kim, "token-a").andExpect(status().isNoContent());   // 같은 토큰은 한 줄

		pushTokenService.push(kim.getId(), linkNotification());

		assertThat(pushSender.sent).containsExactly(new RecordingPushSender.Sent(List.of("token-a"), "이에오",
				"한빛로지스에서 단골 연결을 요청했어요", Map.of("type", "DRIVER_LINK_REQUESTED", "notificationId", "7",
						"driverLinkId", "3", "createdAt", "2026-11-13T05:00:00Z")));
		assertThat(tokenRepository.findAllByUserId(kim.getId())).hasSize(1);
	}

	@Test
	void 다른_사람이_같은_기기로_로그인하면_토큰_주인이_바뀌고_로그아웃하면_지운다() throws Exception {
		token(kim, "token-a");
		token(park, "token-a");
		assertThat(tokenRepository.findAllByUserId(kim.getId())).isEmpty();
		assertThat(tokenRepository.findAllByUserId(park.getId())).hasSize(1);

		perform(post("/api/notifications/push-token/remove").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"token-a\"}"), park).andExpect(status().isNoContent());
		assertThat(tokenRepository.findAllByUserId(park.getId())).isEmpty();

		pushTokenService.push(park.getId(), linkNotification());
		assertThat(pushSender.sent).isEmpty();
	}

	@Test
	void 더는_쓸_수_없는_토큰은_지운다() throws Exception {
		token(kim, "token-a");
		token(kim, "token-gone");
		pushSender.gone.add("token-gone");

		pushTokenService.push(kim.getId(), linkNotification());

		assertThat(tokenRepository.findAllByUserId(kim.getId())).extracting("token").containsExactly("token-a");
	}

	@Test
	void 빈_토큰은_400() throws Exception {
		token(kim, "").andExpect(status().isBadRequest());
	}

	private NotificationResponse linkNotification() {
		return new NotificationResponse(7L, NotificationType.DRIVER_LINK_REQUESTED, "한빛로지스에서 단골 연결을 요청했어요",
				null, null, 3L, Instant.parse("2026-11-13T05:00:00Z"));
	}

	private ResultActions token(User user, String token) throws Exception {
		return perform(put("/api/notifications/push-token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\"}"), user);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, User user) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(user.getId()).accessToken()));
	}
}
