package com.ieo.server.domain.broker.enums;

import java.util.Set;
import lombok.Getter;

/**
 * 주선사 플랜 (12장). 30일 가격(원, 가정값)은 여기 코드에 둔다. 결제는 데모다 (BILL-007).
 * 2026-11-14: 베이직·프로 둘만 둔다. 담당자 수는 두 플랜 모두 무제한이고, 돈은 리포트(와 나중에 붙일 에이전트)에만 받는다.
 * 프로 플러스는 담당자 무제한 말고 줄 것이 없어 뺐다
 *
 * 기사님 플랜은 plan 도메인의 DriverPlan
 */
@Getter
public enum BrokerPlan {
	BASIC("베이직", 0, Set.of()),
	PRO("프로", 39_000, Set.of(BrokerFeature.REPORTS, BrokerFeature.DRIVER_RECORDS));

	private final String label;
	/** 30일 가격 (원) */
	private final int price;
	private final Set<BrokerFeature> features;

	BrokerPlan(String label, int price, Set<BrokerFeature> features) {
		this.label = label;
		this.price = price;
		this.features = features;
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
