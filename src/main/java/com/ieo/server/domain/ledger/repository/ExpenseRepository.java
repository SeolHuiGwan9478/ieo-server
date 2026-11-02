package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.Expense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

	/** 기간 경비. 최근 날짜 먼저 */
	List<Expense> findAllByDriverUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(Long userId, LocalDate from, LocalDate to);

	List<Expense> findAllByTripId(Long tripId);
}
