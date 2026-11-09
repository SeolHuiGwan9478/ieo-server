package com.ieo.server.domain.ledger.controller;

import com.ieo.server.domain.ledger.dto.CreateLedgerEntryRequest;
import com.ieo.server.domain.ledger.dto.DailySummaryResponse;
import com.ieo.server.domain.ledger.dto.DistanceRequest;
import com.ieo.server.domain.ledger.dto.ExtraIncomeResponse;
import com.ieo.server.domain.ledger.dto.LedgerEntryResponse;
import com.ieo.server.domain.ledger.dto.ReceivablesResponse;
import com.ieo.server.domain.ledger.dto.UpdateLedgerEntryRequest;
import com.ieo.server.domain.ledger.service.LedgerExportService;
import com.ieo.server.domain.ledger.service.LedgerService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 장부 (06 장부). 차주 계정만 쓸 수 있다. 날짜는 한국 날짜(yyyy-MM-dd) */
@RestController
@RequiredArgsConstructor
public class LedgerController {

	private final LedgerService ledgerService;
	private final LedgerExportService ledgerExportService;

	/** 기간 장부. 비우면 이번 달 */
	@GetMapping("/api/ledger")
	public List<LedgerEntryResponse> getEntries(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ledgerService.getEntries(userId, from, to);
	}

	/** 기간 장부를 CSV 파일로 받는다 (LEDG-033) */
	@GetMapping("/api/ledger/export")
	public ResponseEntity<byte[]> export(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to) {
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ieo-ledger-" + from + "_" + to + ".csv\"")
				.body(ledgerExportService.export(userId, from, to));
	}

	/** 직접 추가 */
	@PostMapping("/api/ledger")
	@ResponseStatus(HttpStatus.CREATED)
	public LedgerEntryResponse addManual(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody CreateLedgerEntryRequest request) {
		return ledgerService.addManual(userId, request);
	}

	/** 운임·결제 방식·지급 예정일 고치기 */
	@PatchMapping("/api/ledger/{entryId}")
	public LedgerEntryResponse update(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long entryId,
			@Valid @RequestBody UpdateLedgerEntryRequest request) {
		return ledgerService.update(userId, entryId, request);
	}

	/** 직접 추가한 줄만 지운다 */
	@DeleteMapping("/api/ledger/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long entryId) {
		ledgerService.delete(userId, entryId);
	}

	/** 운임 받음 */
	@PostMapping("/api/ledger/{entryId}/paid")
	public LedgerEntryResponse markPaid(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long entryId) {
		return ledgerService.setPaid(userId, entryId, true);
	}

	/** 받음 되돌리기 */
	@DeleteMapping("/api/ledger/{entryId}/paid")
	public LedgerEntryResponse unmarkPaid(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long entryId) {
		return ledgerService.setPaid(userId, entryId, false);
	}

	/** 대기비 받음 */
	@PostMapping("/api/ledger/extra-incomes/{extraIncomeId}/paid")
	public ExtraIncomeResponse markExtraIncomePaid(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long extraIncomeId) {
		return ledgerService.setExtraIncomePaid(userId, extraIncomeId, true);
	}

	@DeleteMapping("/api/ledger/extra-incomes/{extraIncomeId}/paid")
	public ExtraIncomeResponse unmarkExtraIncomePaid(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long extraIncomeId) {
		return ledgerService.setExtraIncomePaid(userId, extraIncomeId, false);
	}

	/** 미수금 */
	@GetMapping("/api/ledger/receivables")
	public ReceivablesResponse getReceivables(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return ledgerService.getReceivables(userId);
	}

	/** 하루 요약. 비우면 오늘 */
	@GetMapping("/api/ledger/daily")
	public DailySummaryResponse getDailySummary(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @RequestParam(required = false) LocalDate date) {
		return ledgerService.getDailySummary(userId, date);
	}

	/** 차량 앱: 끝난 운행의 주행거리 */
	@PutMapping("/api/trips/{tripId}/distance")
	public LedgerEntryResponse changeDistance(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@Valid @RequestBody DistanceRequest request) {
		return ledgerService.changeDistance(userId, tripId, request.distanceKm());
	}
}
