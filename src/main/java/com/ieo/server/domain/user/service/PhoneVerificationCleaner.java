package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.repository.PhoneTokenRepository;
import com.ieo.server.domain.user.repository.PhoneVerificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 phoneToken과 하루 넘게 안 쓴 인증 기록을 매일 새벽에 지운다.
 * TODO: 운영 시 Redis로 변경 예정. 그때는 TTL로 지워지므로 이 작업은 없앤다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneVerificationCleaner {

	private static final Duration KEEP_VERIFICATION = Duration.ofDays(2);

	private final PhoneTokenRepository phoneTokenRepository;
	private final PhoneVerificationRepository phoneVerificationRepository;
	private final Clock clock;

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	@Transactional
	public void clean() {
		Instant now = clock.instant();
		int tokens = phoneTokenRepository.deleteExpired(now);
		int verifications = phoneVerificationRepository.deleteStale(now.minus(KEEP_VERIFICATION), now);
		log.info("휴대폰 인증 정리: phoneToken {}개, 인증 기록 {}개", tokens, verifications);
	}
}
