package com.ieo.server.domain.notification.service;

import com.ieo.server.domain.dispatch.event.DispatchRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 배차 요청이 저장되면 만료 시각(요청 + 10분)에 한 번 확인한다. 메모리에만 있어서 서버가 다시 켜지면 잃는다.
 * 그때는 앱이 다시 붙을 때 NotificationService가 놓친 만료를 채운다
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DispatchExpiryTimer {

	private final TaskScheduler scheduler;
	private final NotificationService notificationService;

	@TransactionalEventListener
	void on(DispatchRequested event) {
		scheduler.schedule(() -> {
			try {
				notificationService.expire(event.requestId());
			} catch (RuntimeException e) {
				log.warn("배차 요청 만료 알림 실패 requestId={}", event.requestId(), e);
			}
		}, event.expiresAt());
	}
}
