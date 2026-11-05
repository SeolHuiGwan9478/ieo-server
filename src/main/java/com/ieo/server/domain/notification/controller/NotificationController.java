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

/** 내 앱 알림 (09 알림) */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

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
}
