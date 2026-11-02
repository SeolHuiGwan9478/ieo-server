package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 경비 (LEDG-010). spentOn을 비우면 오늘. tripId를 비우고 오늘 쓴 경비면 진행 중인 운행에 붙인다
 */
public record CreateExpenseRequest(
		LocalDate spentOn,
		@NotNull ExpenseCategory category,
		@NotNull Integer amount,
		@Size(max = 100) String memo,
		Long tripId) {
}
