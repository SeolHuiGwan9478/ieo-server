package com.ieo.server.domain.broker.enums;

import java.util.Set;
import lombok.Getter;

/**
 * 주선사 플랜 (12장). 기능 목록·한도·30일 가격(원, 가정값)은 여기 코드에 둔다. 결제는 데모다 (BILL-007).
 * 한도가 null이면 무제한. 담당자 한도와 리포트만 지금 실제로 검사하고,
 * 화주 공유 링크·오더 템플릿은 기능이 생길 때 검사를 붙인다.
 *
 * 기사님 플랜은 plan 도메인의 DriverPlan
 */
@Getter
public enum BrokerPlan {
	BASIC("베이직", 0, Set.of(), 1, 20, 5),
	PRO("프로", 39_000, Set.of(BrokerFeature.REPORTS, BrokerFeature.DRIVER_RECORDS), 10, null, null),
	PRO_PLUS("프로 플러스", 99_000, Set.of(BrokerFeature.values()), null, null, null);

	private final String label;
	/** 30일 가격 (원) */
	private final int price;
	private final Set<BrokerFeature> features;
	/** 담당자(대표 포함) 수. null이면 무제한 */
	private final Integer memberLimit;
	/** 화주 공유 링크 월 건수 (BRK-022, 기능 아직 없음) */
	private final Integer shareLinkMonthlyLimit;
	/** 오더 템플릿 수 (기능 아직 없음) */
	private final Integer orderTemplateLimit;

	BrokerPlan(String label, int price, Set<BrokerFeature> features, Integer memberLimit, Integer shareLinkMonthlyLimit,
			Integer orderTemplateLimit) {
		this.label = label;
		this.price = price;
		this.features = features;
		this.memberLimit = memberLimit;
		this.shareLinkMonthlyLimit = shareLinkMonthlyLimit;
		this.orderTemplateLimit = orderTemplateLimit;
	}

	public boolean allows(BrokerFeature feature) {
		return features.contains(feature);
	}

	/** 이 기능을 쓸 수 있는 가장 낮은 플랜. 업그레이드 안내 문구에 쓴다 */
	public static BrokerPlan lowestWith(BrokerFeature feature) {
		for (BrokerPlan plan : values()) {
			if (plan.allows(feature)) {
				return plan;
			}
		}
		throw new IllegalArgumentException("어느 플랜에도 없는 기능: " + feature);
	}
}
