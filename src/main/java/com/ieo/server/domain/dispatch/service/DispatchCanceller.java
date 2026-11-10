package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.CancelReason;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.service.TripLocationService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 배차 확정 뒤 취소 (DSP-030, 031). 차주가 운행을 취소하든 주선사가 배차를 취소하든 같은 일을 한다:
 * 운행 취소 → 확정된 요청 CANCELED → 오더 다시 모집(또는 내림) → 취소 기록.
 * TODO: 상대방 알림은 09 알림을 만든 뒤 붙인다
 */
@Component
@RequiredArgsConstructor
public class DispatchCanceller {

	private final DispatchRequestRepository requestRepository;
	private final CancellationRepository cancellationRepository;
	private final OrderRepository orderRepository;
	private final TripLocationService locationService;

	/** reopen이면 오더를 다시 모집하고, 아니면 내린다. 차주가 취소하면 늘 다시 모집한다 */
	public Cancellation cancel(Trip trip, User canceledBy, CancelReason reason, String memo, boolean reopen, Instant now) {
		if (reason == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "취소 사유를 골라 주세요.");
		}
		Order order = trip.getOrder();
		trip.cancel(now);
		requestRepository.findFirstByOrderIdAndDriverUserIdOrderByIdDesc(order.getId(), trip.getDriver().getUserId())
				.ifPresent(request -> request.cancel());
		if (reopen) {
			order.reopen();
		} else {
			order.closeAfterCancel();
		}
		Cancellation cancellation = cancellationRepository.save(Cancellation.of(trip, canceledBy, reason, memo));
		// 끝난 운행의 위치는 지운다. 차주 취소는 TripService도 지우지만 주선사 취소는 여기만 지난다
		locationService.clear(trip);
		try {
			orderRepository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new BusinessException(ErrorCode.ORDER_CONFLICT);
		}
		return cancellation;
	}
}
