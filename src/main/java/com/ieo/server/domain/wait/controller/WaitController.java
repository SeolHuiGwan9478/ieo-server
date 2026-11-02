package com.ieo.server.domain.wait.controller;

import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.wait.dto.EditWaitRequest;
import com.ieo.server.domain.wait.dto.WaitProofLinkResponse;
import com.ieo.server.domain.wait.dto.WaitRecordResponse;
import com.ieo.server.domain.wait.dto.WaitTimeRequest;
import com.ieo.server.domain.wait.service.WaitService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운행의 상하차지 대기 (02 대기). {stopType}은 PICKUP 또는 DROPOFF.
 * 진입·작업 시작·이탈은 차량 앱이 판단한 시각을 at으로 보낸다(비우면 서버 시각).
 */
@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class WaitController {

	private final WaitService waitService;

	/** 운행의 대기 기록 (상차, 하차) */
	@GetMapping("/waits")
	public List<WaitRecordResponse> getWaits(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return waitService.getWaits(userId, tripId);
	}

	/** 진입: 지오펜스 안 3분 정차. 새로 기록하면 201, 이미 진입했으면 그 기록과 200 */
	@PostMapping("/stops/{stopType}/wait/arrive")
	public ResponseEntity<WaitRecordResponse> arrive(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@PathVariable StopType stopType,
			@RequestBody(required = false) WaitTimeRequest request) {
		WaitService.Arrival arrival = waitService.arrive(userId, tripId, stopType, at(request));
		return ResponseEntity.status(arrival.created() ? HttpStatus.CREATED : HttpStatus.OK).body(arrival.record());
	}

	/** 작업 시작: "상차 시작"·"하차 시작" */
	@PostMapping("/stops/{stopType}/wait/work-start")
	public WaitRecordResponse startWork(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@PathVariable StopType stopType,
			@RequestBody(required = false) WaitTimeRequest request) {
		return waitService.startWork(userId, tripId, stopType, at(request));
	}

	/** 이탈: 상차지면 운송 중, 하차지면 운행 완료 */
	@PostMapping("/stops/{stopType}/wait/depart")
	public WaitRecordResponse depart(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@PathVariable StopType stopType,
			@RequestBody(required = false) WaitTimeRequest request) {
		return waitService.depart(userId, tripId, stopType, at(request));
	}

	/** 시각 고치기 */
	@PatchMapping("/stops/{stopType}/wait")
	public WaitRecordResponse edit(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@PathVariable StopType stopType,
			@Valid @RequestBody EditWaitRequest request) {
		return waitService.edit(userId, tripId, stopType, request);
	}

	/** 증빙 링크 만들기. 이미 있으면 같은 링크 */
	@PostMapping("/stops/{stopType}/wait/proof")
	public WaitProofLinkResponse createProof(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@PathVariable StopType stopType) {
		return waitService.createProof(userId, tripId, stopType);
	}

	private static Instant at(WaitTimeRequest request) {
		return request == null ? null : request.at();
	}
}
