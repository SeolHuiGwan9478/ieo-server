package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMetrics;
import com.ieo.server.domain.broker.repository.BrokerMetricsRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 신뢰 지표(BrokerMetrics)를 장부·대기비·대기 기록으로 다시 계산한다 (BRK-030).
 * 상하차지 통계처럼 더해 가지 않고 늘 처음부터 다시 계산한다.
 *
 * 부르는 곳: 장부가 생길 때(운행 완료), 받음·되돌리기, 장부 수정, 인수증 서명(지급 예정일이 바뀜),
 * 작업 시작·대기 시각 수정. 부르는 쪽 트랜잭션 안에서 계산해서 방금 바뀐 값이 바로 반영된다.
 * 지급 예정일은 날짜만 지나도 지연이 되므로 매일 새벽 전체를 다시 계산한다 (recalculateAll).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerMetricsCalculator {

	/** 지급은 30~60일 뒤라 상하차지 통계(90일)보다 길게 본다 */
	static final int WINDOW_DAYS = 180;

	private final BrokerMetricsRepository metricsRepository;
	private final BrokerRepository brokerRepository;
	private final TripRepository tripRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final ExtraIncomeRepository extraIncomeRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final Clock clock;

	/** 우리 회사 오더로 배차된 운행일 때만 그 회사를 다시 계산한다. 차주가 직접 등록한 운행·회사 없는 운행은 무시 */
	@Transactional
	public void recalculate(Trip trip) {
		if (trip != null && trip.getSource() == TripSource.IEO_DISPATCH && trip.getBroker() != null) {
			recalculate(trip.getBroker());
		}
	}

	@Transactional
	public void recalculate(Broker broker) {
		Instant now = clock.instant();
		LocalDate today = KoreanDate.of(now);
		LocalDate since = today.minusDays(WINDOW_DAYS);
		Long brokerId = broker.getId();

		int sampleCount = (int) tripRepository.countByBrokerIdAndSourceAndStatusAndCompletedAtGreaterThanEqual(
				brokerId, TripSource.IEO_DISPATCH, TripStatus.COMPLETED,
				since.atStartOfDay(KoreanDate.ZONE).toInstant());

		// 선불·착불은 현장에서 바로 받아서 지급 기간을 따질 게 없다. 인수증 결제만 본다
		List<LedgerEntry> entries = ledgerEntryRepository.findAllForBrokerMetrics(
				brokerId, TripSource.IEO_DISPATCH, PaymentType.RECEIPT, since);
		Integer avgPaymentDays = average(entries.stream()
				.filter(LedgerEntry::isPaid)
				.mapToLong(entry -> Math.max(0, ChronoUnit.DAYS.between(entry.getWorkDate(), KoreanDate.of(entry.getPaidAt()))))
				.toArray());
		List<LedgerEntry> due = entries.stream().filter(entry -> today.isAfter(entry.getExpectedPaymentDate())).toList();
		Integer latePaymentPercent = percent(due.stream().filter(BrokerMetricsCalculator::isLate).count(), due.size());

		List<ExtraIncome> dueWaitFees = extraIncomeRepository.findAllForBrokerMetrics(
						brokerId, TripSource.IEO_DISPATCH, since).stream()
				.filter(income -> today.isAfter(income.getLedgerEntry().getExpectedPaymentDate()))
				.toList();
		Integer waitFeePaidPercent = percent(dueWaitFees.stream().filter(ExtraIncome::isPaid).count(), dueWaitFees.size());

		Integer avgWaitMinutes = average(waitRecordRepository.findAllForBrokerMetrics(
						brokerId, TripSource.IEO_DISPATCH, since.atStartOfDay(KoreanDate.ZONE).toInstant()).stream()
				.map(WaitRecord::waitMinutes)
				.filter(Objects::nonNull)
				.mapToLong(Long::longValue)
				.toArray());

		BrokerMetrics metrics = metricsRepository.findById(brokerId).orElseGet(() -> BrokerMetrics.of(broker));
		metrics.recalculate(sampleCount, avgPaymentDays, latePaymentPercent, waitFeePaidPercent, avgWaitMinutes, now);
		metricsRepository.save(metrics);
	}

	/**
	 * 매일 새벽: 지표가 있는 회사를 모두 다시 계산한다 (날짜가 지나 지연이 된 건, 기간에서 빠진 건 반영).
	 * TODO: 상하차지 통계와 같이 자동 실행은 꺼 두었다. 켤 때 아래 주석을 푼다.
	 * 처음부터 다시 계산하는 방식이라 서버 여러 대에서 같이 돌아도 결과는 같다
	 */
	// @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
	@Transactional
	public void recalculateAll() {
		List<Long> brokerIds = metricsRepository.findAllBrokerIds();
		brokerRepository.findAllById(brokerIds).forEach(this::recalculate);
		log.info("주선사 신뢰 지표 다시 계산: {}곳", brokerIds.size());
	}

	/** 지급 예정일보다 늦게 받았거나 아직 못 받음 */
	private static boolean isLate(LedgerEntry entry) {
		return !entry.isPaid() || KoreanDate.of(entry.getPaidAt()).isAfter(entry.getExpectedPaymentDate());
	}

	private static Integer average(long[] values) {
		if (values.length == 0) {
			return null;
		}
		long sum = 0;
		for (long value : values) {
			sum += value;
		}
		return Math.round((float) sum / values.length);
	}

	private static Integer percent(long count, int total) {
		return total == 0 ? null : Math.round(count * 100f / total);
	}
}
