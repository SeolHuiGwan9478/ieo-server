package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.BrokerTripPageResponse;
import com.ieo.server.domain.broker.dto.BrokerTripResponse;
import com.ieo.server.domain.broker.enums.BrokerTripTab;
import com.ieo.server.domain.broker.service.BrokerTripService;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 진행 중 운행 확인 (BRK-020, 021). 우리 회사 오더로 배차된 운행만 */
@RestController
@RequestMapping("/api/brokers/me/trips")
@RequiredArgsConstructor
public class BrokerTripController {

	private final BrokerTripService tripService;

	/** tab: IN_PROGRESS(기본)·SCHEDULED·FINISHED. page는 0부터, size 기본 20 최대 100 */
	@GetMapping
	public BrokerTripPageResponse getTrips(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) BrokerTripTab tab,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return tripService.getTrips(userId, tab, page, size);
	}

	@GetMapping("/{tripId}")
	public BrokerTripResponse getTrip(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return tripService.getTrip(userId, tripId);
	}
}
