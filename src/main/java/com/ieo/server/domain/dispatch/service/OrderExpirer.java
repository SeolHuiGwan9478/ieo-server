package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상차 가능 시간이 끝났는데 아직 모집 중인 오더를 마감한다 (closeReason = EXPIRED).
 * 차주 마켓은 pickupUntil로 이미 걸러 보여 주지 않지만, 주선사 콘솔의 "모집 중" 탭과 홈 숫자에는 계속 남아
 * 주선사가 하나씩 내려야 했다. 대기 중이던 배차 요청은 주선사가 내릴 때와 같이 마감(DECLINED) 처리한다.
 * 실제 주기 실행은 OrderExpireScheduler가 한다. 여기 두면 테스트에서 바로 부를 수 있다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirer {

	private final OrderRepository orderRepository;
	private final DispatchConfirmer confirmer;
	private final Clock clock;

	/** 마감한 오더 수를 돌려준다 */
	@Transactional
	public int expireOverdue() {
		Instant now = clock.instant();
		List<Order> overdue = orderRepository.findAllByStatusAndPickupUntilLessThanEqual(OrderStatus.OPEN, now);
		int count = 0;
		for (Order order : overdue) {
			if (order.expire(now)) {
				confirmer.declineWaiting(order.getId(), null, now);
				count++;
			}
		}
		if (count > 0) {
			log.info("상차 시간이 지난 오더 자동 마감: {}건", count);
		}
		return count;
	}
}
