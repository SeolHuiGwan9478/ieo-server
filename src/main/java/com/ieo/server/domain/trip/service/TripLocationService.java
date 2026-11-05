package com.ieo.server.domain.trip.service;

import com.ieo.server.domain.trip.dto.TripLocationRequest;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripPosition;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.event.TripProgressed;
import com.ieo.server.domain.trip.repository.TripPositionRepository;
import java.time.Clock;
import java.time.Duration;
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

	/** 길 위를 달리는 상태. 상하차지에 머무는 상태에서는 남은 거리를 쓰지 않는다 */
	private static final Set<TripStatus> MOVING = Set.of(TripStatus.TO_PICKUP, TripStatus.IN_TRANSIT);

	private final TripPositionRepository positionRepository;
	private final ApplicationEventPublisher events;
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
		}
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
