package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.trip.enums.PaymentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 장부 한 줄. auto는 운행이 끝나 자동으로 생긴 줄. overdue는 지급 예정일이 지났는데 받지 못함(빨간색, LEDG-022).
 * waitMinutes는 운행의 상차·하차 대기 합계(자동 줄만)
 */
public record LedgerEntryResponse(
		Long id,
		Long tripId,
		boolean auto,
		LocalDate workDate,
		String description,
		int fare,
		PaymentType paymentType,
		String paymentTypeLabel,
		Long brokerId,
		String brokerName,
		Integer distanceKm,
		Long waitMinutes,
		LocalDate expectedPaymentDate,
		boolean paid,
		Instant paidAt,
		boolean overdue,
		List<ExtraIncomeResponse> extraIncomes) {

	public static LedgerEntryResponse of(LedgerEntry entry, LocalDate today, Long waitMinutes,
			List<ExtraIncomeResponse> extraIncomes) {
		return new LedgerEntryResponse(
				entry.getId(),
				entry.getTrip() == null ? null : entry.getTrip().getId(),
				entry.getTrip() != null,
				entry.getWorkDate(),
				entry.getDescription(),
				entry.getFare(),
				entry.getPaymentType(),
				entry.getPaymentType().getLabel(),
				entry.getBroker() == null ? null : entry.getBroker().getId(),
				entry.brokerDisplayName(),
				entry.getDistanceKm(),
				waitMinutes,
				entry.getExpectedPaymentDate(),
				entry.isPaid(),
				entry.getPaidAt(),
				entry.isOverdue(today),
				extraIncomes);
	}
}
