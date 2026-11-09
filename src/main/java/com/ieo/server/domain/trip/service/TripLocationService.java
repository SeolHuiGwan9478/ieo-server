package com.ieo.server.domain.trip.service;

import com.ieo.server.domain.trip.dto.TripLocationRequest;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.service.ConsentService;
import com.ieo.server.domain.trip.entity.TripPosition;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.notification.service.BrokerNotifier;
import com.ieo.server.domain.trip.event.TripProgressed;
import com.ieo.server.domain.trip.repository.TripPositionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운행 중 위치 받기와 진행률 (TRIP-011~013).
 * 위치 묶음에서 가장 늦게 잰 것 하나로 마지막 위치를 덮어쓰고,
 * 운송 중 진행률이 50%, 67%를 넘으면 TripProgressed를 낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripLocationService {

	/** 진행률 알림 기준 (TRIP-012) */
	static final List<Integer> PROGRESS_MARKS = List.of(50, 67);

	/** 차 시계가 조금 빠를 수 있어 1분까지는 받는다 */
	private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

	private static final int HALF_WAY = 50;
	private static final int NEAR_ARRIVAL_SECONDS = 30 * 60;
	private static final Duration ETA_CHANGE = Duration.ofMinutes(15);
	private static final Duration ETA_RENOTICE_GAP = Duration.ofMinutes(30);
	private static final DateTimeFormatter ETA_TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"));

	/** 길 위를 달리는 상태. 상하차지에 머무는 상태에서는 남은 거리를 쓰지 않는다 */
	private static final Set<TripStatus> MOVING = Set.of(TripStatus.TO_PICKUP, TripStatus.IN_TRANSIT);

	private final TripPositionRepository positionRepository;
	private final ApplicationEventPublisher events;
	private final BrokerNotifier brokerNotifier;
	private final ConsentService consentService;
	private final Clock clock;

	/** 끝난 운행이면 조용히 무시한다: 차량 앱이 쌓아 둔 위치를 버릴 수 있게 */
	public void record(Trip trip, TripLocationRequest request) {
		if (!TripStatus.IN_PROGRESS.contains(trip.getStatus())) {
			return;
		}
		Instant now = clock.instant();
		Optional<TripLocationRequest.Point> latest = request.points().stream()
				.filter(point -> !point.recordedAt().isAfter(now.plus(CLOCK_SKEW)))
				.max(Comparator.comparing(TripLocationRequest.Point::recordedAt));
		if (latest.isEmpty()) {
			return;
		}
		TripLocationRequest.Point point = latest.get();
		Optional<TripPosition> saved = positionRepository.findById(trip.getId());
		TripPosition position = saved.orElseGet(() -> TripPosition.of(trip));
		int before = position.progressPercent().orElse(0);
		Integer remainBefore = position.getRemainSeconds();
		boolean moving = MOVING.contains(trip.getStatus());
		if (!position.update(point.recordedAt(), point.lat(), point.lng(),
				point.remainMeters(), point.remainSeconds(), moving)) {
			return;
		}
		if (saved.isEmpty()) {
			positionRepository.save(position);
		}
		if (trip.getStatus() == TripStatus.IN_TRANSIT) {
			int after = position.progressPercent().orElse(0);
			PROGRESS_MARKS.stream()
					.filter(mark -> before < mark && after >= mark)
					.forEach(mark -> events.publishEvent(new TripProgressed(trip.getId(), mark, point.recordedAt())));
			noticeEta(trip, point, before < HALF_WAY && after >= HALF_WAY, remainBefore, now);
		}
	}

	/**
	 * 주선사에게 도착 예정 시각을 알린다 (TRIP-021, 022): 처음 ETA를 알았을 때(출발), 진행률 50%, 도착 30분 전.
	 * ETA가 15분 이상 바뀌면 30분에 한 번까지 다시 알린다
	 */
	private void noticeEta(Trip trip, TripLocationRequest.Point point, boolean halfWay, Integer remainBefore, Instant now) {
		if (point.remainSeconds() == null || trip.getBroker() == null || !trip.isEtaNoticeEnabled()) {
			return;
		}
		Instant eta = point.recordedAt().plusSeconds(point.remainSeconds());
		boolean first = trip.getEtaSharedAt() == null;
		boolean nearArrival = remainBefore != null && remainBefore > NEAR_ARRIVAL_SECONDS
				&& point.remainSeconds() <= NEAR_ARRIVAL_SECONDS;
		boolean changed = !first && Duration.between(trip.getNotifiedEta(), eta).abs().compareTo(ETA_CHANGE) >= 0
				&& !now.isBefore(trip.getEtaSharedAt().plus(ETA_RENOTICE_GAP));
		if (!first && !halfWay && !nearArrival && !changed) {
			return;
		}
		if (!consentService.isAgreed(trip.getDriver().getUserId(), ConsentType.BROKER_SHARE)) {
			return;
		}
		brokerNotifier.send(trip.getBroker(), "[이에오] " + trip.getVehicle().getPlateNumber() + " 차량이 "
				+ trip.dropoff().getSite().getName() + "에 " + ETA_TIME.format(eta) + "쯤 도착해요.");
		trip.markEtaShared(eta, now);
	}

	/** 마지막 위치. 주선사 콘솔 운행 화면이 쓴다 */
	@Transactional(readOnly = true)
	public Optional<TripPosition> latest(Long tripId) {
		return positionRepository.findById(tripId);
	}

	/** 운행이 끝나면 위치를 지운다 */
	public void clear(Trip trip) {
		positionRepository.deleteById(trip.getId());
	}
}
