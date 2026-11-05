package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.broker.service.BrokerMetricsCalculator;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.util.KoreanDate;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운행이 끝나면 장부 한 줄을 자동으로 만든다 (LEDG-001). 운행이 곧 장부다.
 * 청구 가능 대기비가 있는 대기 기록마다 부가 수입(청구 예정)을 붙인다 (LEDG-002, WAIT-024).
 * 하차지 이탈로 끝나든 완료 버튼으로 끝나든 같은 곳을 부르고, 이미 있으면 다시 만들지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LedgerRecorder {

	private final LedgerEntryRepository entryRepository;
	private final ExtraIncomeRepository extraIncomeRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final BrokerMetricsCalculator metricsCalculator;

	@Transactional
	public void recordTrip(Trip trip) {
		if (entryRepository.existsByTripId(trip.getId())) {
			return;
		}
		LedgerEntry entry = entryRepository.save(
				LedgerEntry.fromTrip(trip, KoreanDate.of(trip.getCompletedAt()), settlementDays(trip)));
		for (WaitRecord record : waitRecordRepository.findAllByTripStopTripId(trip.getId())) {
			Integer amount = record.chargeableAmount();
			if (amount != null && amount > 0) {
				extraIncomeRepository.save(ExtraIncome.waitFee(entry, record, amount));
			}
		}
		metricsCalculator.recalculate(trip);
	}

	/** 인수증이 서명되면 인수증 결제의 지급 예정일을 서명일 기준으로 다시 잡는다 (POD-008) */
	@Transactional
	public void onDeliveryProofSigned(Trip trip, LocalDate signedOn) {
		entryRepository.findByTripId(trip.getId())
				.ifPresent(entry -> entry.rebaseOnDeliveryProof(signedOn, settlementDays(trip)));
		metricsCalculator.recalculate(trip);
	}

	/** 정산 일수: 오더의 지급 예정일 → 주선사 정산 주기 → 30일 */
	private static int settlementDays(Trip trip) {
		Order order = trip.getOrder();
		if (order != null) {
			return order.getExpectedPaymentDays();
		}
		if (trip.getBroker() != null) {
			return trip.getBroker().getSettlementDays();
		}
		return LedgerEntry.DEFAULT_SETTLEMENT_DAYS;
	}
}
