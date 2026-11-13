package com.ieo.server.domain.notification.service;

import com.ieo.server.domain.notification.dto.NotificationResponse;
import com.ieo.server.domain.notification.entity.PushToken;
import com.ieo.server.domain.notification.repository.PushTokenRepository;
import com.ieo.server.global.push.PushSender;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 휴대폰 푸시 기기 등록과 보내기 (09 알림). 앱 안 알림(SSE)과 같은 알림을 꺼져 있는 휴대폰에도 보낸다 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

	/** 푸시 제목. 본문은 알림 문장 그대로 */
	static final String TITLE = "이에오";

	private final PushTokenRepository tokenRepository;
	private final PushSender pushSender;
	private final Clock clock;

	/** 로그인한 휴대폰의 토큰. 다른 사람이 쓰던 기기면 주인을 바꾼다 */
	@Transactional
	public void register(Long userId, String token) {
		tokenRepository.findByToken(token).ifPresentOrElse(
				found -> found.belongTo(userId, clock.instant()),
				() -> tokenRepository.save(PushToken.of(userId, token, clock.instant())));
	}

	/** 로그아웃한 휴대폰 */
	@Transactional
	public void remove(Long userId, String token) {
		tokenRepository.deleteByUserIdAndToken(userId, token);
	}

	/** 탈퇴 */
	@Transactional
	public void removeAll(Long userId) {
		tokenRepository.deleteAllByUserId(userId);
	}

	/**
	 * 알림 하나를 그 사람의 휴대폰들에 보낸다. 알림을 저장한 트랜잭션이 끝난 뒤에 부르므로 새 트랜잭션에서 지운다.
	 * data의 type·notificationId·orderId·driverLinkId로 앱이 열 화면을 정한다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void push(Long userId, NotificationResponse notification) {
		List<String> tokens = tokenRepository.findAllByUserId(userId).stream().map(PushToken::getToken).toList();
		if (tokens.isEmpty()) {
			return;
		}
		Map<String, String> data = new HashMap<>();
		data.put("type", notification.type().name());
		data.put("notificationId", notification.id().toString());
		data.put("createdAt", notification.createdAt().toString());
		if (notification.orderId() != null) {
			data.put("orderId", notification.orderId().toString());
		}
		if (notification.driverLinkId() != null) {
			data.put("driverLinkId", notification.driverLinkId().toString());
		}
		List<String> gone = pushSender.send(tokens, TITLE, notification.message(), data);
		if (!gone.isEmpty()) {
			tokenRepository.deleteAllByTokenIn(gone);
		}
	}
}
