package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 늦은 취소 패널티 (DSP-033). 차주가 30일 안에 늦은 취소(상차 예정 2시간 이내)를 3번 하면,
 * 3번째 취소 때부터 7일 동안 배차를 요청할 수 없다. 차주가 직접 취소한 것만 센다(주선사가 취소한 것은 빼고).
 * 크레딧을 돌려주지 않는 것은 DSP-034로 미뤘다.
 */
@Component
@RequiredArgsConstructor
public class LateCancelPenalty {

	static final int LIMIT = 3;
	static final Duration WINDOW = Duration.ofDays(30);
	static final Duration BLOCK = Duration.ofDays(7);

	private final CancellationRepository cancellationRepository;

	/** 지금 막혀 있으면 풀리는 때. 아니면 비어 있다 */
	public Optional<Instant> blockedUntil(Long driverUserId, Instant now) {
		List<Instant> lates = cancellationRepository.findAllByCanceledById(driverUserId).stream()
				.filter(Cancellation::isLate)
				.map(Cancellation::getCreatedAt)
				.sorted()
				.toList();
		return blockedUntil(lates, now);
	}

	/** [lates]는 늦은 취소 시각, 오래된 순 */
	static Optional<Instant> blockedUntil(List<Instant> lates, Instant now) {
		Instant until = null;
		for (int i = LIMIT - 1; i < lates.size(); i++) {
			Instant third = lates.get(i);
			if (!third.isAfter(lates.get(i - LIMIT + 1).plus(WINDOW))) {
				Instant end = third.plus(BLOCK);
				until = until == null || end.isAfter(until) ? end : until;
			}
		}
		return until != null && now.isBefore(until) ? Optional.of(until) : Optional.empty();
	}
}
