package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.ledger.entity.TaxInvoiceDraft;
import com.ieo.server.domain.ledger.enums.TaxInvoiceProvider;

/**
 * 세금계산서를 실제로 내보내는 곳 (LEDG-045). 지금은 데모 발행기 하나다.
 * 볼타 대리 정발행(LEDG-040, 046)을 붙일 때 이 인터페이스의 구현만 바꾼다
 */
public interface TaxInvoiceIssuer {

	TaxInvoiceProvider provider();

	/** 발행하고 승인번호를 돌려준다 */
	String issue(TaxInvoiceDraft draft);
}
