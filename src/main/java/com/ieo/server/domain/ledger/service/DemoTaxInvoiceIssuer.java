package com.ieo.server.domain.ledger.service;

import com.ieo.server.domain.ledger.entity.TaxInvoiceDraft;
import com.ieo.server.domain.ledger.enums.TaxInvoiceProvider;
import com.ieo.server.global.util.KoreanDate;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 데모 발행 (LEDG-045). 국세청에 보내지 않고 승인번호 모양(작성일 8자리-8자리-8자리)만 만든다.
 * 가운데 자리를 99999999로 두어 진짜 승인번호와 헷갈리지 않게 한다
 */
@Component
@RequiredArgsConstructor
public class DemoTaxInvoiceIssuer implements TaxInvoiceIssuer {

	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;

	@Override
	public TaxInvoiceProvider provider() {
		return TaxInvoiceProvider.DEMO;
	}

	@Override
	public String issue(TaxInvoiceDraft draft) {
		return KoreanDate.today(clock).format(DAY) + "-99999999-" + "%08d".formatted(random.nextInt(100_000_000));
	}
}
