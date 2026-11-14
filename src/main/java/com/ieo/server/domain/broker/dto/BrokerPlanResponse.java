package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerSubscription;
import com.ieo.server.domain.broker.enums.BrokerFeature;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.plan.entity.PlanPayment;
import java.time.Instant;
import java.util.List;

/**
 * 우리 회사 플랜과 쓸 수 있는 기능 (PLAN-002). 콘솔은 features로 메뉴를 잠그고, 서버는 API마다 다시 검사한다.
 * price는 30일 가격(원). 결제는 데모다. 담당자 수는 플랜과 상관없이 무제한 (2026-11-14).
 * periodEnd는 프로의 지금 30일이 끝나는 때(베이직이면 null). cancelAtPeriodEnd면 그때 베이직으로 내려가고, 아니면 그때 다음 30일을 결제한다.
 * startedAt은 플랜을 한 번도 안 바꾼 회사(BASIC)면 null
 */
public record BrokerPlanResponse(
		BrokerPlan plan,
		String planLabel,
		int price,
		List<Feature> features,
		Instant startedAt,
		Instant periodEnd,
		boolean cancelAtPeriodEnd) {

	public record Feature(BrokerFeature code, String label) {
	}

	public static BrokerPlanResponse basic() {
		return of(BrokerPlan.BASIC, null, null, false);
	}

	/** effective는 지금 쓸 수 있는 플랜(기간이 끝났으면 BASIC, 아직 정리 전인 자동 갱신이면 그대로) */
	public static BrokerPlanResponse of(BrokerSubscription subscription, BrokerPlan effective, Instant now) {
		if (effective == BrokerPlan.BASIC) {
			return of(BrokerPlan.BASIC, subscription.getStartedAt(), null, false);
		}
		Instant end = subscription.getPeriodEnd();
		// 새벽 정리 전에 기간이 지난 자동 갱신 구독은 다음 30일로 보여 준다
		while (end != null && !now.isBefore(end)) {
			end = end.plus(PlanPayment.PERIOD);
		}
		return of(effective, subscription.getStartedAt(), end, subscription.isCancelAtPeriodEnd());
	}

	private static BrokerPlanResponse of(BrokerPlan plan, Instant startedAt, Instant periodEnd, boolean cancelAtPeriodEnd) {
		return new BrokerPlanResponse(
				plan,
				plan.getLabel(),
				plan.getPrice(),
				plan.getFeatures().stream().sorted()
						.map(feature -> new Feature(feature, feature.getLabel()))
						.toList(),
				startedAt,
				periodEnd,
				cancelAtPeriodEnd);
	}
}
