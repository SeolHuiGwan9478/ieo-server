package com.ieo.server.domain.dispatch.controller;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 재위탁 의심 오더 목록 (DSP-041). /api/admin/** 은 AdminInterceptor가 운영자만 통과시킨다 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOrderController {

	private final OrderRepository orderRepository;

	public record SuspectedOrder(Long orderId, String brokerName, Long sourceOrderId, String sourceBrokerName,
			String pickupAddress, String dropoffAddress, Instant pickupFrom, int fare, Instant createdAt) {
	}

	@GetMapping("/suspected-orders")
	@Transactional(readOnly = true)
	public List<SuspectedOrder> suspectedOrders(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return orderRepository.findAllByReconsignmentSuspectOfIsNotNullOrderByCreatedAtDesc(
						PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100))).stream()
				.map(this::toRow)
				.toList();
	}

	private SuspectedOrder toRow(Order order) {
		Order source = orderRepository.findById(order.getReconsignmentSuspectOf()).orElse(null);
		return new SuspectedOrder(order.getId(), order.getBroker().getName(), order.getReconsignmentSuspectOf(),
				source == null ? null : source.getBroker().getName(), order.getPickupSite().getAddress(),
				order.getDropoffSite().getAddress(), order.getPickupFrom(), order.getFare(), order.getCreatedAt());
	}
}
