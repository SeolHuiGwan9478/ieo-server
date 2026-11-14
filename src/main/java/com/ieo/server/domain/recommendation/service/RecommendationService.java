package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.service.BrokerMetricsService;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.document.service.DriverVerification;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.plan.enums.DriverFeature;
import com.ieo.server.domain.plan.service.DriverPlanService;
import com.ieo.server.domain.recommendation.dto.RecommendationListResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationResponse;
import com.ieo.server.domain.recommendation.entity.RecommendationPreference;
import com.ieo.server.domain.recommendation.entity.RecommendationShown;
import com.ieo.server.domain.recommendation.repository.RecommendationPreferenceRepository;
import com.ieo.server.domain.recommendation.repository.RecommendationShownRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteStats;
import com.ieo.server.domain.site.repository.SiteStatsRepository;
import com.ieo.server.domain.recommendation.service.DriverOrigins.DriverOrigin;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배차 추천 (03, 2026-11-13 TMAP 호출 줄이기). 차를 세우면(기어 P 또는 주차 브레이크) 차량 앱이 세운 자리를 보낸다.
 * 서버가 지금 운행을 보고 어디 근처를 찾을지 정한다.
 * <ul>
 *   <li>상차지에서 대기·상차 중: 상차지 근처</li>
 *   <li>하차지에서 대기·하차 중: 하차지 근처</li>
 *   <li>출발한 운행이 없음: 세운 자리 근처</li>
 *   <li>그 밖(상차지로 가는 중, 운송 중): 추천하지 않는다</li>
 * </ul>
 * 직선거리로 어림해 상위 3건만 TMAP 실제 경로로 다시 계산하고, 실수익 1등 1건만 준다.
 * 준 것은 recommendation_shown에 적고, 같은 운행의 같은 상하차지(운행이 없으면 10분 안, 1km 안)에서는 그 기록을 다시 돌려준다.
 * 다시 돌려줄 때는 fresh=false라 차량 앱이 큰 창을 다시 띄우지 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

	/** 실제 경로로 다시 계산하는 후보 수. TMAP 호출 수와 같다 */
	static final int EXACT_CANDIDATES = 3;
	/** 운행이 없을 때 이전 추천을 다시 쓰는 시간과 거리 */
	static final Duration REUSE_FOR = Duration.ofMinutes(10);
	static final double REUSE_METERS = 1_000;
	private static final int CANDIDATE_LIMIT = 100;
	private static final Comparator<Candidate> BY_PROFIT = Comparator.comparingInt(Candidate::netProfit).reversed()
			.thenComparingLong(candidate -> candidate.estimate().emptyMeters());

	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final OrderRepository orderRepository;
	private final DispatchRequestRepository requestRepository;
	private final SiteStatsRepository siteStatsRepository;
	private final RecommendationPreferenceRepository preferenceRepository;
	private final RecommendationShownRepository shownRepository;
	private final DriverOrigins origins;
	private final BrokerMetricsService metricsService;
	private final ProfitEstimator profitEstimator;
	private final RecommendationProperties properties;
	private final Clock clock;
	private final DriverVerification driverVerification;
	private final DriverPlanService planService;

	/**
	 * 추천 1건 (REC-001). 기본 차량에 맞고, 제외할 화물이 아니고, 아직 걸어 둔 요청이 없는 오더만.
	 * 프로가 아니거나(2026-11-14), 추천을 껐거나, 기본 차량이 없거나, 추천하지 않는 때면 빈 목록. 베이직은 TMAP을 부르지 않는다
	 */
	@Transactional
	public RecommendationListResponse recommend(Long userId, double lat, double lng) {
		Driver driver = requireDriver(userId);
		Site.validateLocation(lat, lng);
		if (!planService.planOf(userId).allows(DriverFeature.RECOMMENDATION)) {
			return RecommendationListResponse.none();
		}
		RecommendationPreference preference = preferenceOf(driver);
		Optional<Vehicle> found = vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId);
		if (!preference.isEnabled() || found.isEmpty()) {
			return RecommendationListResponse.none();
		}
		DriverOrigin origin = origins.of(userId, lat, lng);
		if (origin.driving()) {
			return RecommendationListResponse.none();
		}
		Instant now = clock.instant();
		// 대기 중·확정·확정 뒤 취소한 요청이 있는 오더는 뺀다. 철회·만료된 것은 다시 보여 준다
		Set<Long> requested = requestRepository.findAllByDriverUserIdOrderByCreatedAtDesc(userId).stream()
				.filter(request -> !request.allowsRetry(now))
				.map(request -> request.getOrder().getId())
				.collect(Collectors.toSet());

		Optional<RecommendationShown> earlier = earlierShown(userId, origin, lat, lng, now)
				.filter(shown -> preference.getChangedAt() == null || shown.getShownAt().isAfter(preference.getChangedAt()));
		if (earlier.isPresent()) {
			return replay(earlier.get(), requested, now, userId);
		}

		Vehicle vehicle = found.get();
		List<Candidate> candidates = orderRepository.findOpenNear(userId, now, origin.lat(), origin.lng(),
						preference.getRadiusKm() * 1000.0, CANDIDATE_LIMIT).stream()
				.filter(order -> !requested.contains(order.getId()))
				.filter(order -> order.fits(vehicle))
				.filter(order -> !preference.excludes(order.getCargoCategory()))
				.map(order -> candidate(order, origin.lat(), origin.lng(), vehicle))
				.filter(candidate -> meetsMinimum(candidate, preference))
				.sorted(BY_PROFIT)
				.limit(EXACT_CANDIDATES)
				.toList();
		Optional<Candidate> best = withExactRoutes(candidates, vehicle).stream()
				.filter(candidate -> meetsMinimum(candidate, preference))
				.min(BY_PROFIT);

		if (best.isEmpty()) {
			shownRepository.save(RecommendationShown.nothing(driver, origin.tripId(), origin.stopType(), origin.lat(), origin.lng(), now));
			return new RecommendationListResponse(List.of(), true);
		}
		Candidate chosen = best.get();
		ProfitEstimator.Estimate estimate = chosen.estimate();
		shownRepository.save(RecommendationShown.of(driver, origin.tripId(), origin.stopType(), origin.lat(), origin.lng(),
				chosen.order(), estimate.netProfit(), estimate.fuelCost(), estimate.tollFare(), estimate.emptyMeters(), now));
		return new RecommendationListResponse(List.of(responseOf(chosen.order(), estimate, now, userId)), true);
	}

	private Optional<RecommendationShown> earlierShown(Long userId, DriverOrigin origin, double lat, double lng, Instant now) {
		if (origin.atStop()) {
			return shownRepository.findFirstByDriverUserIdAndTripIdAndStopTypeOrderByShownAtDesc(userId, origin.tripId(), origin.stopType());
		}
		return shownRepository.findFirstByDriverUserIdAndTripIdIsNullAndShownAtAfterOrderByShownAtDesc(userId, now.minus(REUSE_FOR))
				.filter(shown -> shown.isWithin(lat, lng, REUSE_METERS));
	}

	/** 이미 준 추천을 TMAP 없이 다시 돌려준다. 그새 마감됐거나 요청을 걸었으면 비운다 */
	private RecommendationListResponse replay(RecommendationShown shown, Set<Long> requested, Instant now, Long userId) {
		Order order = shown.getOrder();
		if (order == null || !order.isOpenAt(now) || requested.contains(order.getId())) {
			return RecommendationListResponse.none();
		}
		ProfitEstimator.Estimate estimate = new ProfitEstimator.Estimate(shown.getNetProfit(), shown.getFuelCost(),
				shown.getTollFare(), shown.getEmptyMeters());
		return new RecommendationListResponse(List.of(responseOf(order, estimate, now, userId)), false);
	}

	private RecommendationResponse responseOf(Order order, ProfitEstimator.Estimate estimate, Instant now, Long userId) {
		BrokerMetricsResponse metrics = metricsService.getAll(List.of(order.getBroker().getId())).get(order.getBroker().getId());
		boolean verified = driverVerification.isVerified(userId);
		return new RecommendationResponse(
				limitUnless(verified, OrderResponse.of(order, now, estimate.emptyMeters(), null, null, metrics)),
				estimate.emptyMeters(), estimate.fuelCost(), estimate.netProfit(), isLongWait(order.getPickupSite()), estimate.tollFare());
	}

	public RecommendationPreferenceResponse getPreference(Long userId) {
		return RecommendationPreferenceResponse.from(preferenceOf(requireDriver(userId)), available(userId));
	}

	@Transactional
	public RecommendationPreferenceResponse changePreference(Long userId, RecommendationPreferenceRequest request) {
		Driver driver = requireDriver(userId);
		RecommendationPreference preference = preferenceRepository.findById(userId)
				.orElseGet(() -> preferenceRepository.save(RecommendationPreference.defaults(driver)));
		preference.change(request.enabled(), request.radiusKm(), request.minNetProfit(),
				request.excludedCargoCategories(), clock.instant());
		return RecommendationPreferenceResponse.from(preference, available(userId));
	}

	/** 지금 플랜으로 추천을 받을 수 있는지. 앱이 설정에 "프로에서 받아요"를 그린다 */
	private boolean available(Long userId) {
		return planService.planOf(userId).allows(DriverFeature.RECOMMENDATION);
	}

	/** 공차는 출발점(상하차지 또는 세운 자리)에서 시작한다. 경유가는 상차지 시도 평균 */
	private Candidate candidate(Order order, double lat, double lng, Vehicle vehicle) {
		ProfitEstimator.Target target = new ProfitEstimator.Target(order, lat, lng, order.getPickupSite().getAddress());
		return new Candidate(order, target, profitEstimator.rough(target, vehicle));
	}

	private List<Candidate> withExactRoutes(List<Candidate> candidates, Vehicle vehicle) {
		Map<Long, ProfitEstimator.Estimate> exact = profitEstimator.exact(
				candidates.stream().map(Candidate::target).toList(), vehicle);
		return candidates.stream()
				.map(candidate -> new Candidate(candidate.order(), candidate.target(), exact.get(candidate.order().getId())))
				.toList();
	}

	private static boolean meetsMinimum(Candidate candidate, RecommendationPreference preference) {
		return preference.getMinNetProfit() == null || candidate.netProfit() >= preference.getMinNetProfit();
	}

	private boolean isLongWait(Site site) {
		return siteStatsRepository.findById(site.getId())
				.map(SiteStats::getAvgWaitMinutes)
				.filter(minutes -> minutes >= properties.longWaitMinutes())
				.isPresent();
	}

	private RecommendationPreference preferenceOf(Driver driver) {
		return preferenceRepository.findById(driver.getUserId()).orElseGet(() -> RecommendationPreference.defaults(driver));
	}

	private Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}

	private record Candidate(Order order, ProfitEstimator.Target target, ProfitEstimator.Estimate estimate) {

		int netProfit() {
			return estimate.netProfit();
		}
	}

	/** 서류 심사 전 차주에게는 줄인 오더를 보낸다 (DriverVerification) */
	private static OrderResponse limitUnless(boolean verified, OrderResponse order) {
		return verified ? order : order.forUnverified();
	}
}
