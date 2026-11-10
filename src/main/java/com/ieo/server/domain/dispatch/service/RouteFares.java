package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선별 최근 평균 운임 (DSP-044). 노선은 상차지 시·구 → 하차지 시·구 ("경기 평택시 → 대전 대덕구").
 * Ieo에서 배차받아 끝낸 운행만, 최근 90일, 표본이 10건 이상인 노선만 쓴다. 적으면 한두 건에 휘둘려서다
 */
@Component
@RequiredArgsConstructor
public class RouteFares {

	static final int MIN_SAMPLES = 10;
	static final Duration WINDOW = Duration.ofDays(90);

	private final TripRepository tripRepository;
	private final Clock clock;

	/** 오더 id → 평균 운임(원). 표본이 모자란 노선은 빠진다 */
	@Transactional(readOnly = true)
	public Map<Long, Integer> averages(Collection<Order> orders) {
		if (orders.isEmpty()) {
			return Map.of();
		}
		List<Trip> done = tripRepository.findCompletedDispatchSince(clock.instant().minus(WINDOW));
		Map<String, List<Integer>> faresByRoute = done.stream()
				.collect(Collectors.groupingBy(trip -> route(trip.getOrder()),
						Collectors.mapping(Trip::getFare, Collectors.toList())));
		Map<Long, Integer> result = new HashMap<>();
		for (Order order : orders) {
			List<Integer> fares = faresByRoute.get(route(order));
			if (fares != null && fares.size() >= MIN_SAMPLES) {
				result.put(order.getId(), (int) Math.round(fares.stream().mapToInt(Integer::intValue).average().orElse(0)));
			}
		}
		return result;
	}

	static String route(Order order) {
		return region(order.getPickupSite().getAddress()) + " → " + region(order.getDropoffSite().getAddress());
	}

	/** 주소의 앞 두 낱말 ("경기 평택시 포승읍 …" → "경기 평택시") */
	static String region(String address) {
		if (address == null || address.isBlank()) {
			return "";
		}
		String[] words = address.trim().split("\\s+");
		return words.length < 2 ? words[0] : words[0] + " " + words[1];
	}
}
