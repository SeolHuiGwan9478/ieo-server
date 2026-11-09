package com.ieo.server.domain.dispatch.controller;

import com.ieo.server.domain.dispatch.dto.CreateDispatchRequestRequest;
import com.ieo.server.domain.dispatch.dto.CreateOrderReportRequest;
import com.ieo.server.domain.dispatch.dto.DispatchRequestResponse;
import com.ieo.server.domain.dispatch.dto.OrderReportResponse;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.service.DispatchService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 차주의 오더 탐색과 배차 요청 (11 배차). 차주 계정만 쓸 수 있다 */
@RestController
@RequiredArgsConstructor
public class OrderController {

	private final DispatchService dispatchService;

	/** 모집 중 오더. 내 차량으로 맡을 수 있는 것만, 상차가 빠른 순서. 반경 기본 50km, 1~300km */
	@GetMapping("/api/orders")
	public List<OrderResponse> getOpenOrders(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(required = false) Integer radiusM,
			@RequestParam(required = false) Integer size) {
		return dispatchService.getOpenOrders(userId, lat, lng, radiusM, size);
	}

	/** 지금 위치에서 이 오더를 맡을 때의 실수익 내역 */
	@GetMapping("/api/orders/{orderId}/profit")
	public com.ieo.server.domain.recommendation.dto.ProfitEstimateResponse estimateProfit(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long orderId,
			@RequestParam double lat,
			@RequestParam double lng) {
		return dispatchService.estimateProfit(userId, orderId, lat, lng);
	}

	@GetMapping("/api/orders/{orderId}")
	public OrderResponse getOrder(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long orderId) {
		return dispatchService.getOrder(userId, orderId);
	}

	/** 배차 요청. 자동 확정 오더면 바로 CONFIRMED와 tripId가 온다 */
	@PostMapping("/api/orders/{orderId}/requests")
	@ResponseStatus(HttpStatus.CREATED)
	public DispatchRequestResponse request(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long orderId,
			@RequestBody(required = false) CreateDispatchRequestRequest body) {
		return dispatchService.request(userId, orderId, body);
	}

	/** 오더 신고. 오더 하나에 한 번만 */
	@PostMapping("/api/orders/{orderId}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public OrderReportResponse report(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long orderId,
			@Valid @RequestBody CreateOrderReportRequest request) {
		return dispatchService.report(userId, orderId, request);
	}

	/** 내 배차 요청 */
	@GetMapping("/api/dispatch-requests")
	public List<DispatchRequestResponse> getMyRequests(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return dispatchService.getMyRequests(userId);
	}

	/** 요청 철회 */
	@PostMapping("/api/dispatch-requests/{requestId}/withdraw")
	public DispatchRequestResponse withdraw(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long requestId) {
		return dispatchService.withdraw(userId, requestId);
	}
}
