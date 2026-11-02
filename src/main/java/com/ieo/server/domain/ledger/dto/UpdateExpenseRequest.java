package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateExpenseRequest(
		@NotNull LocalDate spentOn,
		@NotNull ExpenseCategory category,
		@NotNull Integer amount,
		@Size(max = 100) String memo) {
}
