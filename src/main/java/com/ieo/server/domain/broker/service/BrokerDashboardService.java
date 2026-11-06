package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerDashboardResponse;
import com.ieo.server.domain.broker.dto.BrokerDashboardResponse.Bucket;
import com.ieo.server.domain.broker.dto.report.ReportPeriod;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.DashboardUnit;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘솔 첫 화면 대시보드. 리포트(BrokerReportService)와 달리 플랜을 안 가리고, 기간을 일·주·월 칸으로 나눠 준다.
 * 직접 등록한 운행(MANUAL)은 리포트와 같은 이유로 빼고, 캐시 없이 조회할 때 센다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerDashboardService {

	private final BrokerMemberRepository memberRepository;
	private final OrderRepository orderRepository;
	private final TripRepository tripRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final Clock clock;

	public BrokerDashboardResponse get(Long userId, DashboardUnit unit, LocalDate from, LocalDate to) {
		BrokerMember member = requireActiveMember(userId);
		Long brokerId = member.getBroker().getId();
		LocalDate end = to == null ? KoreanDate.today(clock) : to;
		ReportPeriod period = ReportPeriod.of(from == null ? unit.defaultFrom(end) : from, end, clock);

		Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
		for (LocalDate start = unit.bucketStart(period.from()); !start.isAfter(period.to()); start = unit.next(start)) {
			LocalDate bucketEnd = unit.next(start).minusDays(1);
			buckets.put(start, Bucket.empty(start.isBefore(period.from()) ? period.from() : start,
					bucketEnd.isAfter(period.to()) ? period.to() : bucketEnd));
		}
		Function<Instant, LocalDate> keyOf = instant -> unit.bucketStart(KoreanDate.of(instant));

		for (Order order : orderRepository.findAllByBrokerIdAndCreatedAtBetween(brokerId, period.start(), period.end())) {
			buckets.computeIfPresent(keyOf.apply(order.getCreatedAt()), (key, bucket) -> bucket.plus(1, 0, 0, 0, 0));
		}
		List<Trip> completed = tripRepository.findAllByBrokerIdAndSourceAndStatusAndCompletedAtBetween(
				brokerId, TripSource.IEO_DISPATCH, TripStatus.COMPLETED, period.start(), period.end());
		Map<Long, Long> waitFeeByTrip = new HashMap<>();
		if (!completed.isEmpty()) {
			for (WaitRecord record : waitRecordRepository.findAllByTripStopTripIdIn(completed.stream().map(Trip::getId).toList())) {
				Integer amount = record.chargeableAmount();
				if (amount != null) {
					waitFeeByTrip.merge(record.getTripStop().getTrip().getId(), amount.longValue(), Long::sum);
				}
			}
		}
		for (Trip trip : completed) {
			long waitFee = waitFeeByTrip.getOrDefault(trip.getId(), 0L);
			Integer fee = trip.getOrder() == null ? null : trip.getOrder().fee();
			buckets.computeIfPresent(keyOf.apply(trip.getCompletedAt()), (key, bucket) -> {
				Bucket added = bucket.plus(0, 1, 0, trip.getFare(), waitFee);
				return fee == null ? added : added.plusPriced(trip.getOrder().getBilledFare(), fee);
			});
		}
		for (Trip trip : tripRepository.findAllByBrokerIdAndSourceAndStatusAndCanceledAtBetween(
				brokerId, TripSource.IEO_DISPATCH, TripStatus.CANCELLED, period.start(), period.end())) {
			buckets.computeIfPresent(keyOf.apply(trip.getCanceledAt()), (key, bucket) -> bucket.plus(0, 0, 1, 0, 0));
		}

		Bucket total = Bucket.empty(period.from(), period.to());
		for (Bucket bucket : buckets.values()) {
			total = total.plus(bucket);
		}
		return new BrokerDashboardResponse(unit, unit.getLabel(), period.from(), period.to(), total,
				new ArrayList<>(buckets.values()));
	}

	private BrokerMember requireActiveMember(Long userId) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}
}
