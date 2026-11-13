package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.recommendation.entity.RecommendationShown;
import com.ieo.server.domain.recommendation.repository.RecommendationShownRepository;
import com.ieo.server.domain.recommendation.service.DriverOrigins.DriverOrigin;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 오더 상세의 실수익 (DSP-011, 2026-11-13 TMAP 줄이기). 이 순서로 찾고, 없을 때만 TMAP에 묻는다.
 * <ol>
 *   <li>같은 곳에서 추천으로 이미 보여 준 오더면 그때 숫자 (recommendation_shown). 추천 카드와 상세 숫자가 같다</li>
 *   <li>같은 기사님이 같은 오더를 10분 안, 1km 안에서 다시 열었으면 직전 숫자 (서버 메모리)</li>
 * </ol>
 * 메모리는 TMAP 약관(24시간 안)보다 훨씬 짧게 들고 있고, 서버가 꺼지면 사라져도 된다
 */
@Component
@RequiredArgsConstructor
public class OrderProfits {

	static final Duration REUSE_FOR = Duration.ofMinutes(10);
	static final double REUSE_METERS = 1_000;
	private static final int MAX_RECENT = 5_000;

	private final ProfitEstimator profitEstimator;
	private final RecommendationShownRepository shownRepository;
	private final Clock clock;
	private final Map<RecentKey, Recent> recent = new ConcurrentHashMap<>();

	public ProfitEstimator.Estimate of(Long userId, Order order, Vehicle vehicle, DriverOrigin origin) {
		Instant now = clock.instant();
		Optional<ProfitEstimator.Estimate> shown = shownEstimate(userId, order, origin, now);
		if (shown.isPresent()) {
			return shown.get();
		}
		RecentKey key = new RecentKey(userId, order.getId());
		Recent earlier = recent.get(key);
		if (earlier != null && earlier.at().isAfter(now.minus(REUSE_FOR)) && origin.isNear(earlier.lat(), earlier.lng(), REUSE_METERS)) {
			return earlier.estimate();
		}
		ProfitEstimator.Target target = new ProfitEstimator.Target(order, origin.lat(), origin.lng(), order.getPickupSite().getAddress());
		ProfitEstimator.Estimate estimate = profitEstimator.exact(List.of(target), vehicle).get(order.getId());
		if (recent.size() >= MAX_RECENT) {
			recent.entrySet().removeIf(entry -> !entry.getValue().at().isAfter(now.minus(REUSE_FOR)));
		}
		recent.put(key, new Recent(origin.lat(), origin.lng(), now, estimate));
		return estimate;
	}

	private Optional<ProfitEstimator.Estimate> shownEstimate(Long userId, Order order, DriverOrigin origin, Instant now) {
		Optional<RecommendationShown> shown = origin.atStop()
				? shownRepository.findFirstByDriverUserIdAndTripIdAndStopTypeOrderByShownAtDesc(userId, origin.tripId(), origin.stopType())
				: shownRepository.findFirstByDriverUserIdAndTripIdIsNullAndShownAtAfterOrderByShownAtDesc(userId, now.minus(REUSE_FOR))
						.filter(record -> record.isWithin(origin.lat(), origin.lng(), REUSE_METERS));
		return shown.filter(record -> record.getOrder() != null && record.getOrder().getId().equals(order.getId()))
				.map(record -> new ProfitEstimator.Estimate(record.getNetProfit(), record.getFuelCost(), record.getTollFare(),
						record.getEmptyMeters()));
	}

	private record RecentKey(Long userId, Long orderId) {
	}

	private record Recent(double lat, double lng, Instant at, ProfitEstimator.Estimate estimate) {
	}
}
