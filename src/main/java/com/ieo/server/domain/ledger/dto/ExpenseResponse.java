package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.entity.Expense;
import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import java.time.LocalDate;

public record ExpenseResponse(
		Long id,
		Long tripId,
		LocalDate spentOn,
		ExpenseCategory category,
		String categoryLabel,
		int amount,
		String memo) {

	public static ExpenseResponse from(Expense expense) {
		return new ExpenseResponse(expense.getId(), expense.getTrip() == null ? null : expense.getTrip().getId(),
				expense.getSpentOn(), expense.getCategory(), expense.getCategory().getLabel(), expense.getAmount(),
				expense.getMemo());
	}
}
