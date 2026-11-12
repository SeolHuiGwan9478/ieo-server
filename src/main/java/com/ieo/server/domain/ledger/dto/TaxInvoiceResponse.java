package com.ieo.server.domain.ledger.dto;

import com.ieo.server.domain.ledger.entity.TaxInvoice;
import com.ieo.server.domain.ledger.entity.TaxInvoiceDraft;
import com.ieo.server.domain.ledger.enums.TaxInvoiceProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 운행 한 건의 세금계산서 (LEDG-040~045). issued=false면 발행 전 초안이고 missing에 빈 정보가 있다.
 * provider가 DEMO면 국세청에 보내지 않은 시연용 발행이다
 */
public record TaxInvoiceResponse(
		Long ledgerEntryId,
		boolean issued,
		LocalDate writeDate,
		Party supplier,
		Party buyer,
		String item,
		int fareAmount,
		int waitFeeAmount,
		int supplyAmount,
		int vat,
		int totalAmount,
		List<String> missing,
		TaxInvoiceProvider provider,
		String approvalNumber,
		Instant issuedAt) {

	public record Party(String businessNumber, String name, String representative) {
	}

	public static TaxInvoiceResponse draft(TaxInvoiceDraft draft) {
		return new TaxInvoiceResponse(draft.entry().getId(), false, draft.entry().getWorkDate(),
				new Party(draft.supplierBusinessNumber(), draft.supplierName(), draft.supplierRepresentative()),
				new Party(draft.buyerBusinessNumber(), draft.buyerName(), draft.buyerRepresentative()),
				draft.item(), draft.fareAmount(), draft.waitFeeAmount(), draft.supplyAmount(), draft.vat(),
				draft.supplyAmount() + draft.vat(), draft.missing(), null, null, null);
	}

	public static TaxInvoiceResponse from(TaxInvoice invoice) {
		return new TaxInvoiceResponse(invoice.getLedgerEntry().getId(), true, invoice.getWriteDate(),
				new Party(invoice.getSupplierBusinessNumber(), invoice.getSupplierName(), invoice.getSupplierRepresentative()),
				new Party(invoice.getBuyerBusinessNumber(), invoice.getBuyerName(), invoice.getBuyerRepresentative()),
				invoice.getItem(), invoice.getFareAmount(), invoice.getWaitFeeAmount(), invoice.getSupplyAmount(), invoice.getVat(),
				invoice.totalAmount(), List.of(), invoice.getProvider(), invoice.getApprovalNumber(), invoice.getIssuedAt());
	}
}
