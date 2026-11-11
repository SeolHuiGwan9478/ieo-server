package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.recommendation.service.TruckSpec;
import com.ieo.server.domain.restarea.dto.RestAreaListResponse;
import com.ieo.server.domain.restarea.dto.RouteRestAreaListResponse;
import com.ieo.server.domain.restarea.dto.RouteRestAreaResponse;
import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운행 중 경로 위 쉴 곳 (TRIP-032, docs/design/08_휴게소.md 3장).
 * 지금 위치에서 이번 목적지(상차지로 가는 중이면 상차지, 그 뒤면 하차지)까지 TMAP 화물차 경로를 받아,
 * 라운지(수면실·샤워실·세탁실)와 졸음쉼터를 경로 거리순으로 준다. 경로는 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripRestAreaService {

	private static final Set<TripStatus> TO_PICKUP = Set.of(TripStatus.SCHEDULED, TripStatus.TO_PICKUP);

	private final TripRepository tripRepository;
	private final RestAreaRepository restAreaRepository;
	private final TruckRouteLineSource routeLines;

	public RouteRestAreaListResponse findOnRoute(Long userId, Long tripId, double lat, double lng) {
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Trip trip = tripRepository.findById(tripId)
				.filter(found -> found.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
		if (trip.getStatus().isFinished()) {
			throw new BusinessException(ErrorCode.TRIP_INVALID_STATUS);
		}
		TripStop stop = TO_PICKUP.contains(trip.getStatus()) ? trip.pickup() : trip.dropoff();
		var route = routeLines.route(lat, lng, stop.getSite().getLat(), stop.getSite().getLng(), TruckSpec.of(trip.getVehicle()));
		if (route.isEmpty()) {
			return new RouteRestAreaListResponse(false, List.of(), RestAreaListResponse.ATTRIBUTION);
		}
		List<RestArea> lounges = restAreaRepository.findAllByMissingSinceIsNull().stream()
				.filter(area -> area.getLocation() != null && area.hasTruckLounge())
				.toList();
		List<RouteRestAreaResponse> items = RouteRestAreaPicker.pick(route.get(), lounges).stream()
				.map(RouteRestAreaResponse::of)
				.toList();
		return new RouteRestAreaListResponse(true, items, RestAreaListResponse.ATTRIBUTION);
	}
}
