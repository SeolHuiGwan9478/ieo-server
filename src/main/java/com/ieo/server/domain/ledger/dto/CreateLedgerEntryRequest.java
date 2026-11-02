package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.trip.enums.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Ieo로 등록하지 않은 운행을 직접 추가 (LEDG-004). 지급 예정일을 비우면 결제 방식으로 계산한다 */
public record CreateLedgerEntryRequest(
		@NotNull LocalDate workDate,
		@NotBlank @Size(max = 100) String description,
		@NotNull Integer fare,
		@NotNull PaymentType paymentType,
		@Size(max = 50) String brokerName,
		LocalDate expectedPaymentDate) {
}
