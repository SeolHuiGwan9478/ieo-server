package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.dto.UpdateBrokerRequest;
import com.ieo.server.domain.broker.service.BrokerMetricsService;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 주선사 회사 (BRK-001).
 * 담당자는 먼저 주선사 역할로 가입(/api/auth/signup)하고, 로그인한 뒤 회사를 등록한다.
 */
@RestController
@RequestMapping("/api/brokers")
@RequiredArgsConstructor
public class BrokerController {

	private final BrokerService brokerService;
	private final BrokerMetricsService metricsService;

	/** 회사 등록. 등록한 계정이 대표가 된다. 이미 등록된 사업자등록번호면 409 BROKER_409_BUSINESS_NUMBER */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MyBrokerResponse register(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateBrokerRequest request) {
		return brokerService.register(userId, request);
	}

	/** 내 회사와 내 직책. 소속이 없으면 404 BROKER_404_MEMBER */
	@GetMapping("/me")
	public MyBrokerResponse getMine(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return brokerService.getMine(userId);
	}

	/** 회사 정보·정산 주기 수정 (대표만, 보낸 항목만). 사업자등록번호는 바꿀 수 없다 */
	@PatchMapping("/me")
	public MyBrokerResponse update(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody UpdateBrokerRequest request) {
		return brokerService.update(userId, request);
	}

	/** 우리 회사 신뢰 지표 (BRK-030~032). 차주 오더 카드에 보이는 것과 같다. 담당자 누구나 */
	@GetMapping("/me/metrics")
	public BrokerMetricsResponse getMyMetrics(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return metricsService.getMine(userId);
	}
}
