package com.ieo.server.domain.dispatch.controller;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
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

	/**
	 * 의심 오더 한 줄. source* 는 먼저 올라온 오더라 운영자가 두 오더를 나란히 견준다 (운영자에겐 오더 상세 화면이 없다).
	 * 먼저 올라온 오더를 찾지 못하면 source* 는 null
	 */
	public record SuspectedOrder(Long orderId, String brokerName, Long sourceOrderId, String sourceBrokerName,
			String pickupAddress, String dropoffAddress, Instant pickupFrom, int fare, Instant createdAt, OrderStatus status,
			String cargoType, String sourcePickupAddress, String sourceDropoffAddress, Instant sourcePickupFrom,
			Integer sourceFare, Instant sourceCreatedAt, OrderStatus sourceStatus, String sourceCargoType) {
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
		boolean found = source != null;
		return new SuspectedOrder(order.getId(), order.getBroker().getName(), order.getReconsignmentSuspectOf(),
				found ? source.getBroker().getName() : null, order.getPickupSite().getAddress(),
				order.getDropoffSite().getAddress(), order.getPickupFrom(), order.getFare(), order.getCreatedAt(),
				order.getStatus(), order.getCargoType(),
				found ? source.getPickupSite().getAddress() : null, found ? source.getDropoffSite().getAddress() : null,
				found ? source.getPickupFrom() : null, found ? source.getFare() : null,
				found ? source.getCreatedAt() : null, found ? source.getStatus() : null,
				found ? source.getCargoType() : null);
	}
}
