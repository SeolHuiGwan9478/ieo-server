package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.trip.enums.PaymentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 운임·결제 방식·지급 예정일·주행거리 고치기 (LEDG-003). 주행거리는 비우면 지운다 (0~3,000km) */
public record UpdateLedgerEntryRequest(
		@NotNull Integer fare,
		@NotNull PaymentType paymentType,
		@NotNull LocalDate expectedPaymentDate,
		Integer distanceKm) {
}
