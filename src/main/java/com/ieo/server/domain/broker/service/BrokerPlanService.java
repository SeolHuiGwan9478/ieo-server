package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerPlanResponse;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerSubscription;
import com.ieo.server.domain.broker.enums.BrokerFeature;
import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.broker.repository.BrokerInvitationRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerSubscriptionRepository;
import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.domain.plan.repository.PlanPaymentRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 플랜 (12장 PLAN-001~004). 구독 줄이 없으면 BASIC.
 * 기능 검사는 각 API의 서비스가 requireFeature를 부른다. 플랜을 내려도 데이터는 지우지 않고 보기·추가만 막는다 (PLAN-004)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerPlanService {

	private final BrokerSubscriptionRepository subscriptionRepository;
	private final BrokerMemberRepository memberRepository;
	private final BrokerInvitationRepository invitationRepository;
	private final PlanPaymentRepository paymentRepository;
	private final Clock clock;

	public BrokerPlan planOf(Long brokerId) {
		return subscriptionRepository.findById(brokerId).map(BrokerSubscription::getPlan).orElse(BrokerPlan.BASIC);
	}

	public BrokerPlanResponse getPlan(Long brokerId) {
		return subscriptionRepository.findById(brokerId)
				.map(subscription -> BrokerPlanResponse.of(subscription.getPlan(), subscription.getStartedAt()))
				.orElseGet(() -> BrokerPlanResponse.of(BrokerPlan.BASIC, null));
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
	 * 담당자를 한 명 더 둘 수 있는지 (초대 보낼 때, 초대 수락할 때). 지금 담당자 + 대기 중인 초대가 한도 이상이면 403.
	 * 플랜을 내려서 이미 한도를 넘은 회사는 담당자를 내보내지 않고 새로 들어오는 것만 막는다
	 */
	public void requireMemberRoom(Long brokerId) {
		Integer limit = planOf(brokerId).getMemberLimit();
		if (limit == null) {
			return;
		}
		Instant now = clock.instant();
		long members = memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(brokerId).size();
		long pending = invitationRepository
				.findAllByBrokerIdAndStatusOrderByCreatedAtDesc(brokerId, BrokerInvitationStatus.PENDING).stream()
				.filter(invitation -> invitation.isPendingAt(now))
				.count();
		if (members + pending >= limit) {
			throw new BusinessException(ErrorCode.PLAN_MEMBER_LIMIT,
					"지금 플랜은 담당자를 %d명까지 둘 수 있어요. 더 두려면 플랜을 올려 주세요.".formatted(limit));
		}
	}

	/** 초대를 수락해 들어올 때는 대기 중인 초대 자신이 한 자리를 이미 차지하고 있으므로 담당자 수만 본다 */
	public void requireMemberRoomForAccept(Long brokerId) {
		Integer limit = planOf(brokerId).getMemberLimit();
		if (limit != null && memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(brokerId).size() >= limit) {
			throw new BusinessException(ErrorCode.PLAN_MEMBER_LIMIT,
					"이 회사의 플랜은 담당자를 %d명까지 둘 수 있어서 지금은 합류할 수 없어요. 대표에게 알려 주세요.".formatted(limit));
		}
	}

	/** 플랜 바꾸기. 데모 결제라 바로 적용하고 유료 플랜이면 결제 기록(30일)을 남긴다 (BILL-007). 부르는 쪽이 대표인지 확인한다 */
	@Transactional
	public BrokerPlanResponse change(Broker broker, BrokerPlan plan) {
		Instant now = clock.instant();
		BrokerPlan before = planOf(broker.getId());
		BrokerSubscription subscription = subscriptionRepository.findById(broker.getId())
				.map(found -> {
					found.change(plan, now);
					return found;
				})
				.orElseGet(() -> subscriptionRepository.save(BrokerSubscription.of(broker, plan, now)));
		if (plan.getPrice() > 0 && before != plan) {
			paymentRepository.save(PlanPayment.demo(PlanOwnerType.BROKER, broker.getId(), plan.name(), plan.getPrice(),
					now, now.plus(PlanPayment.PERIOD), now));
		}
		return BrokerPlanResponse.of(subscription.getPlan(), subscription.getStartedAt());
	}
}
