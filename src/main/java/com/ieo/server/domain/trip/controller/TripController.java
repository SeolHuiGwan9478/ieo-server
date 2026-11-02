package com.ieo.server.domain.trip.controller;

import com.ieo.server.domain.trip.dto.CancelTripRequest;
import com.ieo.server.domain.trip.dto.CreateTripRequest;
import com.ieo.server.domain.trip.dto.TripResponse;
import com.ieo.server.domain.trip.service.TripService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 운행 (01 운행). 차주 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

	private final TripService tripService;

	/** 직접 등록 (TRIP-001). 예약 상태로 만든다 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TripResponse register(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateTripRequest request) {
		return tripService.register(userId, request);
	}

	/** 내 운행 목록. 최근 등록한 순서, size 기본 20 최대 50 */
	@GetMapping
	public List<TripResponse> getMyTrips(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @RequestParam(required = false) Integer size) {
		return tripService.getMyTrips(userId, size);
	}

	/** 차량 앱: 지금 진행 중인 운행. 없으면 204 */
	@GetMapping("/current")
	public ResponseEntity<TripResponse> getCurrent(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return tripService.getCurrent(userId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@GetMapping("/{tripId}")
	public TripResponse getTrip(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return tripService.getTrip(userId, tripId);
	}

	/** 출발: 상차지로 이동 */
	@PostMapping("/{tripId}/start")
	public TripResponse start(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return tripService.start(userId, tripId);
	}

	/** 하차 완료 */
	@PostMapping("/{tripId}/complete")
	public TripResponse complete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return tripService.complete(userId, tripId);
	}

	/** 취소. Ieo 배차 운행은 사유(reason)가 필요하고, 오더는 다시 모집된다 */
	@PostMapping("/{tripId}/cancel")
	public TripResponse cancel(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@Valid @RequestBody(required = false) CancelTripRequest request) {
		return tripService.cancel(userId, tripId, request);
	}
}
