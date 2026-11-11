package com.ieo.server.domain.restarea.controller;

import com.ieo.server.domain.restarea.dto.RestAreaListResponse;
import com.ieo.server.domain.restarea.dto.RouteRestAreaListResponse;
import com.ieo.server.domain.restarea.service.RestAreaFinder;
import com.ieo.server.domain.restarea.service.TripRestAreaService;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고속도로 휴게소 (TRIP-032). 응답의 attribution을 화면에 함께 적는다 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RestAreaController {

	private final RestAreaFinder finder;
	private final TripRestAreaService tripRestAreaService;

	/** 지금 자리 근처 화물차 휴게소, 가까운 순. all=false(기본)면 라운지(수면실·샤워실·세탁실)가 있는 곳만, true면 화물 휴게소도 */
	@GetMapping("/rest-areas")
	public RestAreaListResponse findNear(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(defaultValue = "50") int radiusKm,
			@RequestParam(defaultValue = "false") boolean all,
			@RequestParam(defaultValue = "5") int limit) {
		return finder.findNear(lat, lng, radiusKm, all, limit);
	}

	/**
	 * 운행 중 경로 위 쉴 곳. 지금 위치에서 이번 목적지까지 TMAP 화물차 경로를 받아, 경로 위 오른쪽(우리 방향)의
	 * 라운지와 졸음쉼터를 경로 거리순으로. 2km보다 가까운 곳은 뺀다. 경로를 못 받으면 routed=false
	 */
	@GetMapping("/trips/{tripId}/rest-areas")
	public RouteRestAreaListResponse findOnRoute(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@RequestParam double lat,
			@RequestParam double lng) {
		return tripRestAreaService.findOnRoute(userId, tripId, lat, lng);
	}
}
