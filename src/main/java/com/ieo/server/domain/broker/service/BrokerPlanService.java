package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerPlanResponse;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerSubscription;
import com.ieo.server.domain.broker.enums.BrokerFeature;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.broker.repository.BrokerSubscriptionRepository;
import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.domain.plan.repository.PlanPaymentRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 플랜 (12장 PLAN-001~004, PLAN-008, BILL-004). 구독 줄이 없으면 BASIC.
 * 프로는 30일 단위다. 기간이 끝나면 다음 30일을 결제(데모)하고, 해지했으면 베이직으로 내린다.
 * 정리는 바꿀 때와 매일 새벽([settleExpired])에 한다. 읽을 때는 고치지 않고, 아직 정리 전인 자동 갱신 구독은 프로로 본다.
 * 기능 검사는 각 API의 서비스가 requireFeature를 부른다. 플랜을 내려도 데이터는 지우지 않고 보기·추가만 막는다 (PLAN-004)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerPlanService {

	private final BrokerSubscriptionRepository subscriptionRepository;
	private final PlanPaymentRepository paymentRepository;
	private final Clock clock;

	public BrokerPlan planOf(Long brokerId) {
		Instant now = clock.instant();
		return subscriptionRepository.findById(brokerId)
				.map(subscription -> subscription.isExpiredAt(now) && !subscription.isCancelAtPeriodEnd()
						? subscription.getPlan() // 자동 갱신 대상: 새벽 정리나 다음 변경 때 결제(데모)된다
						: subscription.planAt(now))
				.orElse(BrokerPlan.BASIC);
	}

	public BrokerPlanResponse getPlan(Long brokerId) {
		Instant now = clock.instant();
		return subscriptionRepository.findById(brokerId)
				.map(subscription -> BrokerPlanResponse.of(subscription, planOf(brokerId), now))
				.orElseGet(() -> BrokerPlanResponse.basic());
	}

	/** 이 플랜에 없는 기능이면 403 PLAN_403_UPGRADE. 메시지에 필요한 플랜을 넣는다 (PLAN-003 안내용) */
	public void requireFeature(Long brokerId, BrokerFeature feature) {
		BrokerPlan plan = planOf(brokerId);
		if (!plan.allows(feature)) {
			BrokerPlan needed = BrokerPlan.lowestWith(feature);
			throw new BusinessException(ErrorCode.PLAN_UPGRADE_REQUIRED,
					"%s은(는) %s 플랜부터 쓸 수 있어요. 지금은 %s 플랜이에요.".formatted(feature.getLabel(), needed.getLabel(), plan.getLabel()));
		}
	}

	/**
	 * 플랜 고르기 (부르는 쪽이 대표인지 확인한다). 데모 결제라 바로 적용한다 (BILL-007).
	 * 프로: 새 30일을 시작하고 결제 기록을 남긴다. 해지해 둔 프로를 다시 고르면 해지만 취소한다(결제 없음).
	 * 베이직: 해지. 남은 날까지 프로를 쓰고 기간이 끝나면 베이직이 된다
	 */
	@Transactional
	public BrokerPlanResponse change(Broker broker, BrokerPlan plan) {
		if (plan == null) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		Instant now = clock.instant();
		BrokerSubscription subscription = current(broker, now);
		if (plan == BrokerPlan.BASIC) {
			if (subscription.planAt(now) != BrokerPlan.BASIC) {
				subscription.cancel();
			}
		} else if (subscription.planAt(now) == plan) {
			subscription.resume();
		} else {
			subscription.subscribe(plan, now);
			pay(subscription, now);
		}
		return BrokerPlanResponse.of(subscription, subscription.planAt(now), now);
	}

	/** 매일 새벽 기간이 끝난 프로를 다음 30일로 결제(데모)하거나, 해지했으면 베이직으로 내린다 */
	@Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
	@Transactional
	public void settleExpired() {
		Instant now = clock.instant();
		List<BrokerSubscription> expired = subscriptionRepository.findAllByPeriodEndLessThanEqual(now);
		expired.forEach(subscription -> settle(subscription, now));
	}

	/** 기간이 끝난 구독을 정리하고 돌려준다. 줄이 없으면 BASIC 줄을 만든다 */
	private BrokerSubscription current(Broker broker, Instant now) {
		BrokerSubscription subscription = subscriptionRepository.findById(broker.getId())
				.orElseGet(() -> subscriptionRepository.save(BrokerSubscription.basic(broker, now)));
		settle(subscription, now);
		return subscription;
	}

	private void settle(BrokerSubscription subscription, Instant now) {
		while (subscription.isExpiredAt(now)) {
			if (subscription.isCancelAtPeriodEnd()) {
				subscription.downgrade(now);
			} else {
				subscription.renew();
				pay(subscription, now);
			}
		}
	}

	private void pay(BrokerSubscription subscription, Instant now) {
		BrokerPlan plan = subscription.getPlan();
		paymentRepository.save(PlanPayment.demo(PlanOwnerType.BROKER, subscription.getBrokerId(), plan.name(), plan.getPrice(),
				subscription.getPeriodStart(), subscription.getPeriodEnd(), now));
	}
}
