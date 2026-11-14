package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단골 연결이 해제되면 그 차주에게 지정한, 아직 모집 중인 오더를 마감한다 (BRK-011, 2026-11-13).
 * 해제된 차주가 계속 요청하거나, 주선사가 그 오더를 고치려다 "단골 아님"으로 막히는 일을 없앤다.
 * 기다리던 요청은 마감 알림과 함께 닫힌다. 이미 확정된 배차와 운행은 그대로다
 */
@Component
@RequiredArgsConstructor
public class TargetedOrderCloser {

	private final OrderRepository orderRepository;
	private final DispatchConfirmer confirmer;

	@Transactional
	public void closeFor(Long brokerId, Long driverUserId, Instant now) {
		for (Order order : orderRepository.findAllByBrokerIdAndTargetDriverUserIdAndStatus(brokerId, driverUserId, OrderStatus.OPEN)) {
			if (order.closeForUnlink()) {
				confirmer.declineWaiting(order.getId(), null, now);
			}
		}
	}
}
