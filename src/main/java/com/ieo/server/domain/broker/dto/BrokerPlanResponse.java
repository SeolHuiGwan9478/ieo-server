package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.enums.BrokerFeature;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import java.time.Instant;
import java.util.List;

/**
 * 우리 회사 플랜과 쓸 수 있는 기능 (PLAN-002). 콘솔은 features로 메뉴를 잠그고, 서버는 API마다 다시 검사한다.
 * price는 30일 가격(원). 결제는 데모라 고르면 바로 적용되고 결제 기록만 남는다. 한도가 null이면 무제한. startedAt은 플랜을 한 번도 안 바꾼 회사(BASIC)면 null
 */
public record BrokerPlanResponse(
		BrokerPlan plan,
		String planLabel,
		int price,
		List<Feature> features,
		Limits limits,
		Instant startedAt) {

	public record Feature(BrokerFeature code, String label) {
	}

	public record Limits(Integer members, Integer shareLinksPerMonth, Integer orderTemplates) {
	}

	public static BrokerPlanResponse of(BrokerPlan plan, Instant startedAt) {
		return new BrokerPlanResponse(
				plan,
				plan.getLabel(),
				plan.getPrice(),
				plan.getFeatures().stream().sorted()
						.map(feature -> new Feature(feature, feature.getLabel()))
						.toList(),
				new Limits(plan.getMemberLimit(), plan.getShareLinkMonthlyLimit(), plan.getOrderTemplateLimit()),
				startedAt);
	}
}
