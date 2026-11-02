package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.ledger.dto.CreateExpenseRequest;
import com.ieo.server.domain.ledger.dto.ExpenseResponse;
import com.ieo.server.domain.ledger.dto.UpdateExpenseRequest;
import com.ieo.server.domain.ledger.entity.Expense;
import com.ieo.server.domain.ledger.repository.ExpenseRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경비 (LEDG-010, 011). 음성("주유 15만 원")은 차량 앱이 분류와 금액으로 바꿔 보낸다.
 * 오늘 쓴 경비는 진행 중인 운행에 붙여 운행별 실수익에 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

	private final ExpenseRepository expenseRepository;
	private final TripRepository tripRepository;
	private final LedgerService ledgerService;
	private final Clock clock;

	public ExpenseResponse add(Long userId, CreateExpenseRequest request) {
		Driver driver = ledgerService.requireDriver(userId);
		LocalDate today = KoreanDate.today(clock);
		LocalDate spentOn = request.spentOn() != null ? request.spentOn() : today;
		return ExpenseResponse.from(expenseRepository.save(Expense.create(driver, resolveTrip(userId, request.tripId(), spentOn, today),
				spentOn, request.category(), request.amount(), request.memo())));
	}

	/** 기간 경비. 비우면 이번 달. 최근 날짜 먼저 */
	@Transactional(readOnly = true)
	public List<ExpenseResponse> getExpenses(Long userId, LocalDate from, LocalDate to) {
		ledgerService.requireDriver(userId);
		LocalDate today = KoreanDate.today(clock);
		LocalDate start = from != null ? from : today.withDayOfMonth(1);
		LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
		LedgerService.validateRange(start, end);
		return expenseRepository.findAllByDriverUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(userId, start, end).stream()
				.map(ExpenseResponse::from)
				.toList();
	}

	public ExpenseResponse update(Long userId, Long expenseId, UpdateExpenseRequest request) {
		Expense expense = findMine(userId, expenseId);
		expense.change(request.spentOn(), request.category(), request.amount(), request.memo());
		return ExpenseResponse.from(expense);
	}

	public void delete(Long userId, Long expenseId) {
		expenseRepository.delete(findMine(userId, expenseId));
	}

	/** 운행을 고르면 내 운행이어야 한다. 안 고르고 오늘 쓴 경비면 진행 중인 운행에 붙인다 */
	private Trip resolveTrip(Long userId, Long tripId, LocalDate spentOn, LocalDate today) {
		if (tripId != null) {
			return tripRepository.findById(tripId)
					.filter(trip -> trip.isOwnedBy(userId))
					.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
		}
		if (!spentOn.equals(today)) {
			return null;
		}
		return tripRepository.findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(userId, TripStatus.IN_PROGRESS)
				.orElse(null);
	}

	private Expense findMine(Long userId, Long expenseId) {
		ledgerService.requireDriver(userId);
		return expenseRepository.findById(expenseId)
				.filter(expense -> expense.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
	}
}
