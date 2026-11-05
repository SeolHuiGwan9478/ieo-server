package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.service.BrokerMetricsService;
import com.ieo.server.domain.dispatch.dto.CreateDispatchRequestRequest;
import com.ieo.server.domain.dispatch.dto.DispatchRequestResponse;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.dispatch.service.DispatchService;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.recommendation.dto.AnswerRecommendationRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationAnswerResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationListResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationResponse;
import com.ieo.server.domain.recommendation.entity.Recommendation;
import com.ieo.server.domain.recommendation.entity.RecommendationPreference;
import com.ieo.server.domain.recommendation.enums.RecommendationAnswer;
import com.ieo.server.domain.recommendation.repository.RecommendationPreferenceRepository;
import com.ieo.server.domain.recommendation.repository.RecommendationRepository;
import com.ieo.server.domain.recommendation.service.EmptyRunEstimator.EmptyRun;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteStats;
import com.ieo.server.domain.site.repository.SiteStatsRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripPosition;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.trip.service.TripLocationService;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배차 추천 (03). 지금 운행의 하차지 근처에서 이어서 할 오더를 골라 실수익 순으로 준다.
 * 운송 중(IN_TRANSIT)과 하차지 도착(AT_DROPOFF)일 때만 추천한다. 최종 결정은 차주가 한다: 수락해야 배차 요청이 간다.
 * 흐름과 계산식: docs/design/04_배차추천.md
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RecommendationService {

	static final int MAX_ITEMS = 5;
	static final int MAX_REJECTIONS = 3;
	private static final int CANDIDATE_LIMIT = 100;
	private static final Set<TripStatus> RECOMMENDABLE = Set.of(TripStatus.IN_TRANSIT, TripStatus.AT_DROPOFF);

	private final TripRepository tripRepository;
	private final DriverRepository driverRepository;
	private final OrderRepository orderRepository;
	private final DispatchRequestRepository requestRepository;
	private final SiteStatsRepository siteStatsRepository;
	private final RecommendationRepository recommendationRepository;
	private final RecommendationPreferenceRepository preferenceRepository;
	private final TripLocationService locationService;
	private final DispatchService dispatchService;
	private final BrokerMetricsService metricsService;
	private final EmptyRunEstimator emptyRunEstimator;
	private final RecommendationProperties properties;
	private final Clock clock;

	/** 지금 운행 다음 배차 후보. 추천하지 않는 상태(운송 전, 추천 끔, 3번 거절)면 빈 목록 */
	@Transactional(readOnly = true)
	public RecommendationListResponse recommend(Long userId, Long tripId) {
		Trip trip = findMine(userId, tripId);
		RecommendationPreference preference = preferenceOf(trip.getDriver());
		if (!RECOMMENDABLE.contains(trip.getStatus()) || !preference.isEnabled()
				|| recommendationRepository.countByTripIdAndAnswer(tripId, RecommendationAnswer.REJECTED) >= MAX_REJECTIONS) {
			return RecommendationListResponse.none();
		}
		Instant now = clock.instant();
		Site dropoff = trip.dropoff().getSite();
		Instant freeAt = freeAt(trip, dropoff, now);
		Vehicle vehicle = trip.getVehicle();
		double kmPerLiter = kmPerLiter(vehicle);
		Set<Long> skip = skippedOrderIds(userId, trip);

		List<Candidate> candidates = orderRepository.findOpenNear(userId, now, dropoff.getLat(), dropoff.getLng(),
						preference.getRadiusKm() * 1000.0, CANDIDATE_LIMIT).stream()
				.filter(order -> !skip.contains(order.getId()))
				.filter(order -> order.fits(vehicle))
				.map(order -> candidate(order, dropoff, kmPerLiter))
				.filter(candidate -> !freeAt.plus(candidate.run().duration()).isAfter(candidate.order().getPickupUntil()))
				.filter(candidate -> preference.getMinNetProfit() == null || candidate.netProfit() >= preference.getMinNetProfit())
				.sorted(Comparator.comparingInt(Candidate::netProfit).reversed()
						.thenComparingLong(candidate -> candidate.run().meters()))
				.limit(MAX_ITEMS)
				.toList();

		Map<Long, BrokerMetricsResponse> metrics = metricsService.getAll(
				candidates.stream().map(candidate -> candidate.order().getBroker().getId()).toList());
		List<RecommendationResponse> items = candidates.stream()
				.map(candidate -> new RecommendationResponse(
						OrderResponse.of(candidate.order(), now, candidate.run().meters(), null, null,
								metrics.get(candidate.order().getBroker().getId())),
						candidate.run().meters(), candidate.fuelCost(), candidate.netProfit(),
						isLongWait(candidate.order().getPickupSite())))
				.toList();
		return new RecommendationListResponse(freeAt, items);
	}

	/**
	 * 안내한 추천과 대답을 적는다 (REC-020~024). 같은 오더를 다시 적으면 대답을 바꾼다.
	 * 수락이면 이 운행의 차량으로 배차 요청을 보낸다 (REC-023)
	 */
	public RecommendationAnswerResponse answer(Long userId, Long tripId, Long orderId, AnswerRecommendationRequest request) {
		Trip trip = findMine(userId, tripId);
		if (!RECOMMENDABLE.contains(trip.getStatus())) {
			throw new BusinessException(ErrorCode.TRIP_INVALID_STATUS);
		}
		Order order = orderRepository.findById(orderId)
				.filter(found -> found.getTargetDriver() == null || found.getTargetDriver().getUserId().equals(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		RecommendationAnswer answer = request == null ? null : request.answer();

		Recommendation recommendation = recommendationRepository.findByTripIdAndOrderId(tripId, orderId)
				.orElseGet(() -> recommendationRepository.save(Recommendation.presented(trip, order, clock.instant())));
		recommendation.answer(answer);

		DispatchRequestResponse dispatchRequest = null;
		if (answer == RecommendationAnswer.ACCEPTED) {
			dispatchRequest = dispatchService.request(userId, orderId,
					new CreateDispatchRequestRequest(trip.getVehicle().getId()));
		}
		return new RecommendationAnswerResponse(orderId, answer, dispatchRequest);
	}

	@Transactional(readOnly = true)
	public RecommendationPreferenceResponse getPreference(Long userId) {
		return RecommendationPreferenceResponse.from(preferenceOf(requireDriver(userId)));
	}

	public RecommendationPreferenceResponse changePreference(Long userId, RecommendationPreferenceRequest request) {
		Driver driver = requireDriver(userId);
		RecommendationPreference preference = preferenceRepository.findById(userId)
				.orElseGet(() -> preferenceRepository.save(RecommendationPreference.defaults(driver)));
		preference.change(request.enabled(), request.radiusKm(), request.minNetProfit());
		return RecommendationPreferenceResponse.from(preference);
	}

	/**
	 * 하차가 끝날 예정 시각 (REC-002) = 하차지 도착 예정 + 하차지 평균 대기·작업.
	 * 도착 예정은 차량 앱이 보낸 마지막 위치의 ETA. 이미 도착했거나 ETA가 없으면 지금
	 */
	private Instant freeAt(Trip trip, Site dropoff, Instant now) {
		Instant arrival = trip.getStatus() == TripStatus.AT_DROPOFF ? now
				: locationService.latest(trip.getId()).flatMap(TripPosition::eta).filter(now::isBefore).orElse(now);
		Duration finish = siteStatsRepository.findById(dropoff.getId())
				.filter(SiteStats::hasEnoughSamples)
				.filter(stats -> stats.getAvgWaitMinutes() != null && stats.getAvgWorkMinutes() != null)
				.map(stats -> Duration.ofMinutes(stats.getAvgWaitMinutes() + stats.getAvgWorkMinutes()))
				.orElse(Duration.ofMinutes(properties.defaultFinishMinutes()));
		return arrival.plus(finish);
	}

	/** 실측 연비가 있으면 그것, 없으면 톤수별 기본값 (REC-011) */
	private static double kmPerLiter(Vehicle vehicle) {
		return vehicle.getMeasuredKmPerLiter() != null
				? vehicle.getMeasuredKmPerLiter().doubleValue()
				: vehicle.getTonnage().getDefaultKmPerLiter();
	}

	/**
	 * 지금 운행의 오더, 이미 배차 요청한 오더는 뺀다.
	 * 운송 중에는 음성으로 한 건씩 안내하므로 이미 안내한 오더를 빼고,
	 * 하차지에 서 있을 때는 카드로 5건을 보여 주므로 거절한 오더만 뺀다 (REC-020, 021)
	 */
	private Set<Long> skippedOrderIds(Long userId, Trip trip) {
		Set<Long> skip = new HashSet<>();
		if (trip.getOrder() != null) {
			skip.add(trip.getOrder().getId());
		}
		boolean stopped = trip.getStatus() == TripStatus.AT_DROPOFF;
		recommendationRepository.findAllByTripId(trip.getId()).stream()
				.filter(recommendation -> !stopped || recommendation.getAnswer() == RecommendationAnswer.REJECTED)
				.forEach(recommendation -> skip.add(recommendation.getOrder().getId()));
		requestRepository.findAllByDriverUserIdOrderByCreatedAtDesc(userId)
				.forEach(request -> skip.add(request.getOrder().getId()));
		return skip;
	}

	/** 실수익 = 운임 − 공차 연료비 − 통행료(지금은 0) (REC-010) */
	private Candidate candidate(Order order, Site dropoff, double kmPerLiter) {
		EmptyRun run = emptyRunEstimator.estimate(dropoff, order.getPickupSite());
		int fuelCost = (int) Math.round(run.meters() / 1000.0 / kmPerLiter * properties.dieselPriceWon());
		return new Candidate(order, run, fuelCost, order.getFare() - fuelCost);
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

	/** 남의 운행이면 있는지도 알려 주지 않도록 404 */
	private Trip findMine(Long userId, Long tripId) {
		requireDriver(userId);
		return tripRepository.findById(tripId)
				.filter(trip -> trip.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
	}

	private Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}

	private record Candidate(Order order, EmptyRun run, int fuelCost, int netProfit) {
	}
}
