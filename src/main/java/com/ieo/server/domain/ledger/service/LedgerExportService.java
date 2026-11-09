package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.entity.Expense;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.repository.ExpenseRepository;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장부 내보내기 (LEDG-033). 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM을 붙인 CSV를 만든다.
 * 열 구성은 가안이다. 세무사 확인 뒤 바꿀 수 있다 (docs/requirements/06_장부.md).
 * TODO: 플랜별 제한(프로 이상)은 12 요금제를 만든 뒤 붙인다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerExportService {

	private static final String BOM = "﻿";
	private static final String NEW_LINE = "\r\n";
	private static final List<String> HEADER = List.of("일자", "구분", "항목", "거래처", "내용", "결제 방식", "수입금액", "지출금액", "입금 여부", "입금일", "주행거리(km)");

	private final LedgerEntryRepository entryRepository;
	private final ExtraIncomeRepository extraIncomeRepository;
	private final ExpenseRepository expenseRepository;
	private final DriverRepository driverRepository;

	public byte[] export(Long userId, LocalDate from, LocalDate to) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_DRIVER));
		LedgerService.validateRange(from, to);
		List<LedgerEntry> entries = entryRepository.findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(userId, from, to);
		List<ExtraIncome> incomes = entries.isEmpty() ? List.of()
				: extraIncomeRepository.findAllByLedgerEntryIdIn(entries.stream().map(LedgerEntry::getId).toList());
		List<Expense> expenses = expenseRepository.findAllByDriverUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(userId, from, to);

		List<Row> rows = Stream.of(
						entries.stream().map(LedgerExportService::fareRow),
						incomes.stream().map(LedgerExportService::waitFeeRow),
						expenses.stream().map(LedgerExportService::expenseRow))
				.flatMap(stream -> stream)
				.sorted(Comparator.comparing(Row::date))
				.toList();
		return (BOM + render(driver, from, to, rows)).getBytes(StandardCharsets.UTF_8);
	}

	private static String render(Driver driver, LocalDate from, LocalDate to, List<Row> rows) {
		List<List<String>> lines = new ArrayList<>();
		lines.add(List.of("기간", from + " ~ " + to));
		if (driver.getBusinessNumber() != null) {
			lines.add(List.of("상호", driver.getBusinessName()));
			lines.add(List.of("사업자등록번호", driver.getBusinessNumber()));
			lines.add(List.of("대표자", driver.getRepresentativeName()));
		}
		lines.add(List.of());
		lines.add(HEADER);
		rows.forEach(row -> lines.add(row.cells()));
		lines.add(List.of("합계", "", "", "", "", "",
				Long.toString(rows.stream().mapToLong(Row::income).sum()),
				Long.toString(rows.stream().mapToLong(Row::spending).sum())));
		return lines.stream()
				.map(cells -> cells.stream().map(LedgerExportService::cell).collect(Collectors.joining(",")))
				.collect(Collectors.joining(NEW_LINE)) + NEW_LINE;
	}

	private static Row fareRow(LedgerEntry entry) {
		return new Row(entry.getWorkDate(), "수입", "운임", entry.brokerDisplayName(), entry.getDescription(),
				entry.getPaymentType().getLabel(), entry.getFare(), 0, paidLabel(entry.getPaidAt()), paidDate(entry.getPaidAt()),
				entry.getDistanceKm() == null ? "" : entry.getDistanceKm().toString());
	}

	private static Row waitFeeRow(ExtraIncome income) {
		LedgerEntry entry = income.getLedgerEntry();
		return new Row(entry.getWorkDate(), "수입", "대기비", entry.brokerDisplayName(), entry.getDescription(), "",
				income.getAmount(), 0, paidLabel(income.getPaidAt()), paidDate(income.getPaidAt()), "");
	}

	private static Row expenseRow(Expense expense) {
		return new Row(expense.getSpentOn(), "지출", expense.getCategory().getLabel(), "", expense.getMemo(), "",
				0, expense.getAmount(), "", "", "");
	}

	private static String paidLabel(Instant paidAt) {
		return paidAt == null ? "미입금" : "입금";
	}

	private static String paidDate(Instant paidAt) {
		return paidAt == null ? "" : KoreanDate.of(paidAt).toString();
	}

	/** 쉼표·따옴표·줄바꿈을 감싸고, 엑셀이 수식으로 읽을 수 있는 글자로 시작하면 작은따옴표를 붙인다 */
	static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
		return safe.matches("(?s).*[\",\r\n].*") ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
	}

	private record Row(LocalDate date, String kind, String item, String partner, String detail, String payment,
			long income, long spending, String paid, String paidOn, String distance) {

		List<String> cells() {
			return List.of(date.toString(), kind, item, text(partner), text(detail), payment,
					income == 0 ? "" : Long.toString(income), spending == 0 ? "" : Long.toString(spending), paid, paidOn, distance);
		}

		private static String text(String value) {
			return value == null ? "" : value;
		}
	}
}
