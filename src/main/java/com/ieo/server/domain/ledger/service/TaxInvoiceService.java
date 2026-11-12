package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.ledger.dto.TaxInvoiceResponse;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.entity.TaxInvoice;
import com.ieo.server.domain.ledger.entity.TaxInvoiceDraft;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.domain.ledger.repository.TaxInvoiceRepository;
import com.ieo.server.domain.plan.service.DriverPlanService;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운송료 전자세금계산서 (LEDG-040~045). 운행(장부 줄) 1건에 1장, 휴대폰에서 확인하고 발행한다.
 * 지금은 데모 발행기로 DB에만 기록한다. 볼타를 붙이면 TaxInvoiceIssuer 구현만 바꾼다
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaxInvoiceService {

	private final LedgerService ledgerService;
	private final LedgerEntryRepository entryRepository;
	private final ExtraIncomeRepository extraIncomeRepository;
	private final TaxInvoiceRepository taxInvoiceRepository;
	private final DriverPlanService driverPlanService;
	private final TaxInvoiceIssuer issuer;
	private final Clock clock;

	/** 발행했으면 발행한 계산서, 아니면 초안 */
	@Transactional(readOnly = true)
	public TaxInvoiceResponse get(Long userId, Long entryId) {
		LedgerEntry entry = findMine(userId, entryId);
		return taxInvoiceRepository.findByLedgerEntryId(entryId)
				.map(TaxInvoiceResponse::from)
				.orElseGet(() -> TaxInvoiceResponse.draft(draftOf(entry)));
	}

	public TaxInvoiceResponse issue(Long userId, Long entryId) {
		LedgerEntry entry = findMine(userId, entryId);
		if (taxInvoiceRepository.findByLedgerEntryId(entryId).isPresent()) {
			throw new BusinessException(ErrorCode.TAX_INVOICE_ALREADY_ISSUED);
		}
		TaxInvoiceDraft draft = draftOf(entry);
		if (!draft.ready()) {
			throw new BusinessException(ErrorCode.TAX_INVOICE_NOT_READY,
					"세금계산서에 필요한 정보가 비어 있어요: " + String.join(", ", draft.missing()));
		}
		driverPlanService.requireTaxInvoiceRoom(userId);
		String approvalNumber = issuer.issue(draft);
		return TaxInvoiceResponse.from(taxInvoiceRepository.save(
				TaxInvoice.issued(draft, issuer.provider(), approvalNumber, clock.instant())));
	}

	/** 한 달 동안 발행한 계산서 (작성일 기준) */
	@Transactional(readOnly = true)
	public List<TaxInvoiceResponse> getMonth(Long userId, YearMonth month) {
		ledgerService.requireDriver(userId);
		return taxInvoiceRepository
				.findAllByDriverUserIdAndWriteDateBetweenOrderByWriteDateDescIdDesc(userId, month.atDay(1), month.atEndOfMonth()).stream()
				.map(TaxInvoiceResponse::from)
				.toList();
	}

	private TaxInvoiceDraft draftOf(LedgerEntry entry) {
		int waitFee = extraIncomeRepository.findAllByLedgerEntryIdIn(List.of(entry.getId())).stream()
				.mapToInt(ExtraIncome::getAmount)
				.sum();
		return TaxInvoiceDraft.of(entry, waitFee);
	}

	private LedgerEntry findMine(Long userId, Long entryId) {
		ledgerService.requireDriver(userId);
		return entryRepository.findById(entryId)
				.filter(entry -> entry.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
	}
}
