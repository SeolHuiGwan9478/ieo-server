package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import java.time.Instant;

/** 대기비 한 건. 어느 상하차지 대기인지와 받았는지 */
public record ExtraIncomeResponse(
		Long id,
		Long waitRecordId,
		StopType stopType,
		String siteName,
		int amount,
		boolean paid,
		Instant paidAt) {

	public static ExtraIncomeResponse from(ExtraIncome income) {
		TripStop stop = income.getWaitRecord().getTripStop();
		return new ExtraIncomeResponse(income.getId(), income.getWaitRecord().getId(), stop.getType(),
				stop.getSite().getName(), income.getAmount(), income.isPaid(), income.getPaidAt());
	}
}
