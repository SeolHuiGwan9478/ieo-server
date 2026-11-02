package com.ieo.server.domain.dispatch.controller;

import com.ieo.server.domain.dispatch.dto.ApplicantResponse;
import com.ieo.server.domain.dispatch.dto.CancelOrderRequest;
import com.ieo.server.domain.dispatch.dto.CreateOrderRequest;
import com.ieo.server.domain.dispatch.dto.OrderPageResponse;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.dispatch.service.BrokerOrderService;
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

/** 주선사의 오더 관리 (11 배차). 그 주선사 담당자만 쓸 수 있다 */
@RestController
@RequestMapping("/api/broker/orders")
@RequiredArgsConstructor
public class BrokerOrderController {

	private final BrokerOrderService brokerOrderService;

	/** 오더 등록. 필수 항목이 빠지면 400 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse register(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateOrderRequest request) {
		return brokerOrderService.register(userId, request);
	}

	/** 우리 회사 오더. page는 0부터, size 기본 20 최대 100 */
	@GetMapping
	public OrderPageResponse getOrders(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return brokerOrderService.getOrders(userId, status, page, size);
	}

	@GetMapping("/{orderId}")
	public OrderResponse getOrder(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long orderId) {
		return brokerOrderService.getOrder(userId, orderId);
	}

	/** 요청한 차주 목록 */
	@GetMapping("/{orderId}/requests")
	public List<ApplicantResponse> getApplicants(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long orderId) {
		return brokerOrderService.getApplicants(userId, orderId);
	}

	/** 확정. 다른 담당자가 먼저 처리했으면 409 */
	@PostMapping("/{orderId}/requests/{requestId}/confirm")
	public OrderResponse confirm(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long orderId,
			@PathVariable Long requestId) {
		return brokerOrderService.confirm(userId, orderId, requestId);
	}

	/** 확정된 배차 취소. 사유 필수, 오더는 다시 모집(기본)하거나 내린다 */
	@PostMapping("/{orderId}/cancel")
	public OrderResponse cancelDispatch(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long orderId,
			@Valid @RequestBody CancelOrderRequest request) {
		return brokerOrderService.cancelDispatch(userId, orderId, request);
	}

	/** 오더 내리기 */
	@PostMapping("/{orderId}/close")
	public OrderResponse close(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long orderId) {
		return brokerOrderService.close(userId, orderId);
	}
}
