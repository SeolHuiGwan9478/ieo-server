package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.trip.enums.PaymentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 운임·결제 방식·지급 예정일 고치기 (LEDG-003) */
public record UpdateLedgerEntryRequest(
		@NotNull Integer fare,
		@NotNull PaymentType paymentType,
		@NotNull LocalDate expectedPaymentDate) {
}
