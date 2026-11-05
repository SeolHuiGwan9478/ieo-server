package com.ieo.server.domain.notification.service;

import com.ieo.server.domain.notification.dto.NotificationResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 지금 연결된 앱들(SSE). 사용자 한 명이 여러 기기로 붙을 수 있다.
 * 서버 한 대 안에서만 안다. 서버를 여러 대로 늘리면 Redis 같은 것으로 서버끼리 알림을 나눠야 한다.
 * 연결은 30분이면 닫고 앱이 다시 붙는다. 그 사이 20초마다 빈 줄(주석)을 보내 끊긴 연결을 찾고,
 * 중간 장비(로드밸런서)가 조용한 연결을 끊지 않게 한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHub {

	static final Duration TIMEOUT = Duration.ofMinutes(30);
	static final Duration HEARTBEAT = Duration.ofSeconds(20);
	public static final String EVENT_NAME = "notification";

	private final TaskScheduler scheduler;
	private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

	@PostConstruct
	void startHeartbeat() {
		scheduler.scheduleAtFixedRate(this::heartbeat, HEARTBEAT);
	}

	/** 새 연결. 놓친 알림을 먼저 보낸다 */
	public SseEmitter open(Long userId, List<NotificationResponse> missed) {
		SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
		emitters.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
		emitter.onCompletion(() -> remove(userId, emitter));
		emitter.onTimeout(emitter::complete);
		emitter.onError(error -> remove(userId, emitter));
		// 응답 머리를 바로 내보내 앱이 연결됐다는 것을 안다
		if (send(userId, emitter, SseEmitter.event().comment("connected"))) {
			missed.forEach(notification -> send(userId, emitter, event(notification)));
		}
		return emitter;
	}

	public void push(Long userId, NotificationResponse notification) {
		Set<SseEmitter> mine = emitters.get(userId);
		if (mine != null) {
			mine.forEach(emitter -> send(userId, emitter, event(notification)));
		}
	}

	public int connections(Long userId) {
		Set<SseEmitter> mine = emitters.get(userId);
		return mine == null ? 0 : mine.size();
	}

	private void heartbeat() {
		emitters.forEach((userId, mine) -> mine.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
	}

	private static SseEmitter.SseEventBuilder event(NotificationResponse notification) {
		return SseEmitter.event()
				.id(String.valueOf(notification.id()))
				.name(EVENT_NAME)
				.data(notification, MediaType.APPLICATION_JSON);
	}

	/** 못 보내면 끊긴 연결로 보고 뺀다 */
	private boolean send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try {
			emitter.send(event);
			return true;
		} catch (IOException | IllegalStateException e) {
			log.debug("알림 연결 끊김 userId={}: {}", userId, e.getMessage());
			remove(userId, emitter);
			emitter.completeWithError(e);
			return false;
		}
	}

	private void remove(Long userId, SseEmitter emitter) {
		emitters.computeIfPresent(userId, (id, mine) -> {
			mine.remove(emitter);
			return mine.isEmpty() ? null : mine;
		});
	}
}
