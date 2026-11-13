package com.ieo.server.domain.notification.controller;

import com.ieo.server.domain.notification.dto.NotificationResponse;
import com.ieo.server.domain.notification.service.NotificationService;
import com.ieo.server.global.auth.AuthInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ieo.server.domain.notification.dto.PushTokenRequest;
import com.ieo.server.domain.notification.service.PushTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 내 앱 알림 (09 알림) */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final PushTokenService pushTokenService;

	/** after 다음 알림 (없으면 최근 20건). 오래된 순서 */
	@GetMapping
	public List<NotificationResponse> list(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @RequestParam(required = false) Long after) {
		return notificationService.list(userId, after);
	}

	/**
	 * 알림을 바로 받는 연결 (SSE, event: notification, id = 알림 id).
	 * 다시 붙을 때 마지막으로 받은 id를 Last-Event-ID 머리나 after로 주면 그다음부터 먼저 보낸다
	 */
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
			@RequestParam(required = false) Long after) {
		return notificationService.subscribe(userId, lastEventId != null ? lastEventId : after);
	}

	/** 휴대폰 앱이 로그인할 때와 FCM 토큰이 바뀔 때 올린다 (09 알림) */
	@PutMapping("/push-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void registerPushToken(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody PushTokenRequest request) {
		pushTokenService.register(userId, request.token());
	}

	/** 로그아웃할 때. 이 기기로 더는 푸시를 보내지 않는다 */
	@PostMapping("/push-token/remove")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removePushToken(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody PushTokenRequest request) {
		pushTokenService.remove(userId, request.token());
	}
}
