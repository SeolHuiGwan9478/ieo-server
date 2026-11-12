package com.ieo.server.domain.plan.enums;

import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * 기사님 플랜 (12장 PLAN-008, 2026-11-12 크레딧 대신 30일 플랜). 가격은 30일 기준 원(부가세 포함, 가정값).
 * 프로 플러스는 더 줄 것이 없어 뺐다 (2026-11-13). 주선사 플랜은 세 개 그대로.
 * 프로 14,400원은 하루 5건 하는 기사님 원가(약 7,900원)에서도 40%가 남는 값이다. 세금계산서는 장당 99원이라 월 40장으로 막는다
 * 한도가 null이면 무제한. highlights는 휴대폰·차량 앱 티켓에 그대로 적는 세 줄
 */
@Getter
public enum DriverPlan {
	BASIC("베이직", 0, 3, 10, Set.of(),
			List.of("오더·배차·운행 안내", "대기 증빙 30일 3건", "세금계산서 월 10장")),
	PRO("프로", 14_400, null, 40, Set.of(DriverFeature.values()),
			List.of("대기 증빙 무제한", "장부 전체 기간·내보내기", "세금계산서 월 40장·자동 발행"));

	private final String label;
	private final int price;
	/** 최근 30일 동안 주선사에게 보낼 수 있는 대기 증빙 수 (WAIT-022) */
	private final Integer waitProofLimit;
	/** 한 달(한국 날짜 기준 달력 월)에 발행할 수 있는 세금계산서 수 (LEDG-040) */
	private final Integer taxInvoiceMonthlyLimit;
	private final Set<DriverFeature> features;
	private final List<String> highlights;

	DriverPlan(String label, int price, Integer waitProofLimit, Integer taxInvoiceMonthlyLimit, Set<DriverFeature> features,
			List<String> highlights) {
		this.label = label;
		this.price = price;
		this.waitProofLimit = waitProofLimit;
		this.taxInvoiceMonthlyLimit = taxInvoiceMonthlyLimit;
		this.features = features;
		this.highlights = highlights;
	}

	public boolean allows(DriverFeature feature) {
		return features.contains(feature);
	}

	public boolean isPaid() {
		return price > 0;
	}
}
