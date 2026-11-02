package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 배차 확정 (DSP-022). 주선사가 고르거나(SELECT) 첫 요청이 자동으로(FIRST) 확정될 때 같은 일을 한다:
 * 고른 요청 확정 → 오더 배차 완료 → 나머지 대기 요청 마감 → 예약 운행 생성.
 * 다른 담당자가 먼저 바꾼 오더면(@Version) 409로 돌려보낸다.
 * TODO: 확정·마감 알림은 09 알림을 만든 뒤 붙인다
 */
@Component
@RequiredArgsConstructor
class DispatchConfirmer {

	private final OrderRepository orderRepository;
	private final DispatchRequestRepository requestRepository;
	private final TripRepository tripRepository;

	Trip confirm(Order order, DispatchRequest request, Instant now) {
		request.confirm(now);
		order.assign(now);
		requestRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
				.filter(other -> !other.getId().equals(request.getId()))
				.forEach(DispatchRequest::decline);
		Trip trip = tripRepository.save(Trip.fromDispatch(request));
		try {
			orderRepository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new BusinessException(ErrorCode.ORDER_CONFLICT);
		}
		return trip;
	}
}
