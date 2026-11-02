package com.ieo.server.domain.ledger.dto;

import java.util.List;

/**
 * 미수금 (LEDG-021, 022). 운임과 청구 예정 대기비를 따로 합친다.
 * overdue는 지급 예정일이 지난 운임 합계
 */
public record ReceivablesResponse(
		long fareTotal,
		long overdueFareTotal,
		long waitFeeTotal,
		List<LedgerEntryResponse> entries) {
}
