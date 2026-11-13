package com.ieo.server.domain.notification.service;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.dispatch.event.DispatchConfirmed;
import com.ieo.server.domain.dispatch.event.DispatchDeclined;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.notification.dto.NotificationResponse;
import com.ieo.server.domain.notification.entity.Notification;
import com.ieo.server.domain.notification.enums.NotificationType;
import com.ieo.server.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 차주 앱 알림 (09 알림: 배차 확정·거절·만료). 다른 도메인은 이벤트만 내고, 기록과 보내기는 여기서 한다 (NOTI-001).
 * 알림은 배차와 같은 트랜잭션에서 저장하고, 커밋된 뒤에 연결된 앱으로 보낸다. 앱이 꺼져 있어도
 * 다음에 붙을 때 마지막으로 받은 id 다음부터 받는다
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

	/** 한 번에 이어 받는 최대 건수 */
	static final int PAGE_SIZE = 50;
	/** after 없이 부르면 최근 몇 건 */
	static final int RECENT_SIZE = 20;

	private final NotificationRepository notificationRepository;
	private final DispatchRequestRepository requestRepository;
	private final NotificationHub hub;
	private final PushTokenService pushTokenService;
	private final Clock clock;

	@EventListener
	public void on(DispatchConfirmed event) {
		requestRepository.findById(event.requestId()).ifPresent(request -> record(request, NotificationType.DISPATCH_CONFIRMED));
	}

	@EventListener
	public void on(DispatchDeclined event) {
		requestRepository.findById(event.requestId()).ifPresent(request -> record(request, NotificationType.DISPATCH_DECLINED));
	}

	/** 만료 시각에 부른다. 그때까지 확정·철회·마감되지 않았으면 만료 알림 */
	public void expire(Long requestId) {
		requestRepository.findById(requestId)
				.filter(request -> request.statusAt(clock.instant()) == DispatchRequestStatus.EXPIRED)
				.ifPresent(request -> record(request, NotificationType.DISPATCH_EXPIRED));
	}

	/** 단골 연결을 요청받은 (이미 가입한) 차주에게 알린다 (BRK-002). 휴대폰에서 눌러 수락·거절한다 */
	public void linkRequested(BrokerDriverLink link, Long driverUserId) {
		Notification saved = notificationRepository.save(Notification.aboutLink(link, driverUserId, clock.instant()));
		NotificationResponse response = NotificationResponse.of(saved);
		afterCommit(() -> deliver(driverUserId, response));
	}

	/** after 다음 알림. after가 없으면 최근 20건 */
	public List<NotificationResponse> list(Long userId, Long after) {
		fillMissedExpiries(userId);
		List<Notification> found;
		if (after == null) {
			found = new ArrayList<>(notificationRepository.findAllByUserIdOrderByIdDesc(userId, Limit.of(RECENT_SIZE)));
			Collections.reverse(found);
		} else {
			found = notificationRepository.findAllByUserIdAndIdGreaterThanOrderByIdAsc(userId, after, Limit.of(PAGE_SIZE));
		}
		return found.stream().map(NotificationResponse::of).toList();
	}

	/** 앱이 붙는다. after(마지막으로 받은 id)가 있으면 그다음 알림부터 먼저 보낸다 */
	public SseEmitter subscribe(Long userId, Long after) {
		fillMissedExpiries(userId);
		List<NotificationResponse> missed = after == null ? List.of()
				: notificationRepository.findAllByUserIdAndIdGreaterThanOrderByIdAsc(userId, after, Limit.of(PAGE_SIZE))
						.stream().map(NotificationResponse::of).toList();
		return hub.open(userId, missed);
	}

	/** 서버가 다시 켜져 만료 타이머를 잃었을 때를 위해, 만료됐는데 알리지 않은 요청을 채운다 */
	private void fillMissedExpiries(Long userId) {
		Instant until = clock.instant().minus(DispatchRequest.TTL);
		requestRepository.findAllByDriverUserIdAndStatusAndCreatedAtLessThanEqual(userId, DispatchRequestStatus.PENDING, until)
				.forEach(request -> record(request, NotificationType.DISPATCH_EXPIRED));
	}

	private void record(DispatchRequest request, NotificationType type) {
		if (notificationRepository.existsByDispatchRequestIdAndType(request.getId(), type)) {
			return;
		}
		Notification saved = notificationRepository.save(Notification.about(request, type, clock.instant()));
		NotificationResponse response = NotificationResponse.of(saved);
		Long userId = saved.getUserId();
		afterCommit(() -> deliver(userId, response));
	}

	/** 앱이 켜져 있으면 SSE로, 휴대폰에는 푸시로도 보낸다 */
	private void deliver(Long userId, NotificationResponse response) {
		hub.push(userId, response);
		pushTokenService.push(userId, response);
	}

	private static void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
