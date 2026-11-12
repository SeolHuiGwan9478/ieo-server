package com.ieo.server.domain.ledger.enums;

/**
 * 세금계산서를 발행한 곳 (LEDG-045). 지금은 DEMO(서버가 가짜 승인번호를 만들고 국세청에 보내지 않음)뿐이다.
 * 볼타 대리 정발행을 붙이면 BOLTA를 더하고 발행기만 바꾼다
 */
public enum TaxInvoiceProvider {
	DEMO
}
