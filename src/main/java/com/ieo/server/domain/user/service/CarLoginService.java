package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.dto.CarLoginPollResponse;
import com.ieo.server.domain.user.dto.CarLoginRequestResponse;
import com.ieo.server.domain.user.dto.CarLoginSessionResponse;
import com.ieo.server.domain.user.dto.CreateCarLoginSessionRequest;
import com.ieo.server.domain.user.entity.CarLoginSession;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.CarLoginStatus;
import com.ieo.server.domain.user.repository.CarLoginSessionRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.Hashes;
import com.ieo.server.global.util.RandomTokens;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량 QR 로그인. 차량이 세션을 만들어 QR을 띄우고, 로그인된 휴대폰이 code로 승인하면,
 * 차량이 pollSecret으로 확인하다가 토큰을 한 번 받아 간다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CarLoginService {

	static final int POLL_INTERVAL_SECONDS = 2;
	static final String QR_URL_PREFIX = "ieo://car-login?code=";

	/** 0/O, 1/I를 뺀 32글자. 차량 화면에서 읽고 손으로 칠 때 헷갈리지 않게 */
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_GENERATE_ATTEMPTS = 5;

	private final CarLoginSessionRepository carLoginSessionRepository;
	private final UserRepository userRepository;
	private final JwtProvider jwtProvider;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	/**
	 * 차량이 QR 화면을 띄울 때. 로그인 전이라 누구나 부를 수 있다.
	 * TODO: IP별 생성 횟수 제한, IP로 대략 위치 추정 (지금은 approxLocation을 비워 둔다)
	 */
	public CarLoginSessionResponse createSession(CreateCarLoginSessionRequest request, String requestIp) {
		Instant now = clock.instant();
		String code = newCode();
		String pollSecret = RandomTokens.urlSafe();
		CarLoginSession session = carLoginSessionRepository.save(CarLoginSession.create(
				code, Hashes.sha256Hex(pollSecret), request.deviceId(), request.deviceName(), request.appVersion(),
				requestIp, null, now));
		return new CarLoginSessionResponse(session.getId(), code, pollSecret, QR_URL_PREFIX + code,
				session.getExpiresAt(), POLL_INTERVAL_SECONDS);
	}

	/**
	 * 차량이 2초마다 승인 여부를 묻는다. 승인됐으면 이때 토큰을 한 번 발급한다.
	 * pollSecret이 틀리면 세션이 없는 것처럼 404를 준다. 있는지 없는지도 알려 주지 않게.
	 */
	public CarLoginPollResponse poll(UUID sessionId, String pollSecret) {
		CarLoginSession session = carLoginSessionRepository.findByIdForUpdate(sessionId)
				.filter(found -> pollSecret != null && found.matchesPollSecret(Hashes.sha256Hex(pollSecret)))
				.orElseThrow(() -> new BusinessException(ErrorCode.CAR_LOGIN_NOT_FOUND));

		Instant now = clock.instant();
		CarLoginStatus status = session.statusAt(now);
		if (status != CarLoginStatus.APPROVED) {
			return CarLoginPollResponse.of(status);
		}
		User user = session.consume(now);
		user.recordLogin(now);
		return new CarLoginPollResponse(CarLoginStatus.APPROVED, jwtProvider.issue(user.getId()));
	}

	/** 휴대폰이 QR을 찍고 승인 화면을 띄울 때. */
	@Transactional(readOnly = true)
	public CarLoginRequestResponse getRequest(String code) {
		CarLoginSession session = carLoginSessionRepository.findByCode(normalizeCode(code))
				.orElseThrow(() -> new BusinessException(ErrorCode.CAR_LOGIN_NOT_FOUND));
		return CarLoginRequestResponse.of(session, clock.instant());
	}

	/** 승인한 사람의 계정으로 차량이 로그인된다. */
	public void approve(String code, Long userId) {
		CarLoginSession session = findForDecision(code);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		session.approve(user, clock.instant());
	}

	public void reject(String code) {
		findForDecision(code).reject(clock.instant());
	}

	private CarLoginSession findForDecision(String code) {
		return carLoginSessionRepository.findByCodeForUpdate(normalizeCode(code))
				.orElseThrow(() -> new BusinessException(ErrorCode.CAR_LOGIN_NOT_FOUND));
	}

	/** XXXX-XXXX. 겹치면 다시 만든다 (32^8가지라 겹칠 일은 거의 없다). */
	private String newCode() {
		for (int attempt = 0; attempt < CODE_GENERATE_ATTEMPTS; attempt++) {
			StringBuilder code = new StringBuilder(9);
			for (int i = 0; i < 8; i++) {
				if (i == 4) {
					code.append('-');
				}
				code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
			}
			if (!carLoginSessionRepository.existsByCode(code.toString())) {
				return code.toString();
			}
		}
		throw new IllegalStateException("로그인 코드를 만들지 못했습니다.");
	}

	/** 손으로 칠 때 소문자로 치거나 하이픈을 빼도 찾을 수 있게. */
	static String normalizeCode(String code) {
		String compact = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
		return compact.length() == 8 ? compact.substring(0, 4) + "-" + compact.substring(4) : compact;
	}
}
