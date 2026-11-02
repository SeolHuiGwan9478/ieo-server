package com.ieo.server.domain.ledger.controller;

import com.ieo.server.domain.ledger.dto.CreateExpenseRequest;
import com.ieo.server.domain.ledger.dto.ExpenseResponse;
import com.ieo.server.domain.ledger.dto.UpdateExpenseRequest;
import com.ieo.server.domain.ledger.service.ExpenseService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 경비 (LEDG-010, 011). 차주 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;

	/** 경비 남기기. 날짜를 비우면 오늘, 오늘 경비는 진행 중인 운행에 붙는다 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ExpenseResponse add(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateExpenseRequest request) {
		return expenseService.add(userId, request);
	}

	/** 기간 경비. 비우면 이번 달 */
	@GetMapping
	public List<ExpenseResponse> getExpenses(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return expenseService.getExpenses(userId, from, to);
	}

	@PatchMapping("/{expenseId}")
	public ExpenseResponse update(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long expenseId,
			@Valid @RequestBody UpdateExpenseRequest request) {
		return expenseService.update(userId, expenseId, request);
	}

	@DeleteMapping("/{expenseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long expenseId) {
		expenseService.delete(userId, expenseId);
	}
}
