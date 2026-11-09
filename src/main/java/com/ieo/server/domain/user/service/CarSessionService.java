package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.dto.CarSessionResponse;
import com.ieo.server.domain.user.entity.CarSession;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.repository.CarSessionRepository;
import com.ieo.server.global.auth.JwtProperties;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인된 차량 목록과 원격 로그아웃 (ACC-005) */
@Service
@Transactional
@RequiredArgsConstructor
public class CarSessionService {

	private final CarSessionRepository carSessionRepository;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	/** 차량이 로그인할 때. 같은 차의 전 세션은 끝낸다 */
	public UUID open(User user, String deviceId, String deviceName, String appVersion) {
		Instant now = clock.instant();
		carSessionRepository.findAllByUserIdAndDeviceIdAndRevokedAtIsNull(user.getId(), deviceId)
				.forEach(session -> session.revoke(now));
		return carSessionRepository.save(CarSession.open(user, deviceId, deviceName, appVersion, now)).getId();
	}

	@Transactional(readOnly = true)
	public boolean isActive(UUID sessionId) {
		return carSessionRepository.existsByIdAndRevokedAtIsNull(sessionId);
	}

	/** 토큰을 새로 줄 때. 끊긴 세션이면 401 */
	public void touch(UUID sessionId, Long userId) {
		CarSession session = carSessionRepository.findByIdAndUserId(sessionId, userId)
				.filter(CarSession::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
		session.touch(clock.instant());
	}

	/** 리프레시 토큰이 만료될 만큼 안 쓴 차량은 이미 로그아웃된 셈이라 빼고 보여 준다 */
	@Transactional(readOnly = true)
	public List<CarSessionResponse> list(Long userId) {
		Instant after = clock.instant().minus(jwtProperties.refreshTokenTtl());
		return carSessionRepository.findAllByUserIdAndRevokedAtIsNullAndLastUsedAtAfterOrderByLastUsedAtDesc(userId, after)
				.stream()
				.map(CarSessionResponse::from)
				.toList();
	}

	public void revoke(Long userId, UUID sessionId) {
		carSessionRepository.findByIdAndUserId(sessionId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CAR_SESSION_NOT_FOUND))
				.revoke(clock.instant());
	}

	public void revokeAll(Long userId) {
		Instant now = clock.instant();
		carSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach(session -> session.revoke(now));
	}
}
