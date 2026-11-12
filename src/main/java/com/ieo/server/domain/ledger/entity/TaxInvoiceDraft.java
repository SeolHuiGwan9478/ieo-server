package com.ieo.server.domain.ledger.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.entity.Driver;
import java.util.ArrayList;
import java.util.List;

/**
 * 발행 전 세금계산서 (LEDG-041). 장부 줄과 기사님·주선사 정보로 만든다. missing이 비어야 발행할 수 있다
 */
public record TaxInvoiceDraft(
		LedgerEntry entry,
		String supplierBusinessNumber,
		String supplierName,
		String supplierRepresentative,
		String buyerBusinessNumber,
		String buyerName,
		String buyerRepresentative,
		String item,
		int fareAmount,
		int waitFeeAmount,
		List<String> missing) {

	public static TaxInvoiceDraft of(LedgerEntry entry, int waitFeeAmount) {
		Driver driver = entry.getDriver();
		Broker broker = entry.getBroker();
		List<String> missing = new ArrayList<>();
		if (driver.getBusinessNumber() == null) {
			missing.add("내 사업자 정보");
		}
		if (broker == null) {
			missing.add("받는 주선사 (이에오에 가입한 주선사만 발행할 수 있어요)");
		}
		if (entry.getFare() <= 0) {
			missing.add("운임");
		}
		return new TaxInvoiceDraft(entry,
				driver.getBusinessNumber(), driver.getBusinessName(), driver.getRepresentativeName(),
				broker == null ? null : broker.getBusinessNumber(),
				broker == null ? entry.brokerDisplayName() : broker.getName(),
				broker == null ? null : broker.getRepresentativeName(),
				"운송료 " + entry.getDescription(),
				entry.getFare(), waitFeeAmount, List.copyOf(missing));
	}

	public int supplyAmount() {
		return fareAmount + waitFeeAmount;
	}

	/** 공급가액의 10%, 원 미만 버림 */
	public int vat() {
		return supplyAmount() / 10;
	}

	public boolean ready() {
		return missing.isEmpty();
	}
}
