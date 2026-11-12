package com.ieo.server.domain.plan.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.repository.TaxInvoiceRepository;
import com.ieo.server.domain.plan.dto.DriverPlanResponse;
import com.ieo.server.domain.plan.entity.DriverSubscription;
import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.domain.plan.enums.DriverFeature;
import com.ieo.server.domain.plan.enums.DriverPlan;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.domain.plan.repository.DriverSubscriptionRepository;
import com.ieo.server.domain.plan.repository.PlanPaymentRepository;
import com.ieo.server.domain.wait.repository.WaitProofRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사님 플랜 (12장 PLAN-001~008, BILL-004, 007). 결제는 데모: 고르면 바로 성공하고 결제 기록을 남긴다.
 * 기간이 끝난 구독은 읽을 때 정리한다: 해지·체험이면 BASIC으로, 아니면 다음 30일을 데모 결제한다.
 * 다른 서비스는 require*로 기능과 한도를 검사한다. 플랜을 내려도 데이터는 지우지 않는다 (PLAN-004)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DriverPlanService {

	private static final Duration WAIT_PROOF_WINDOW = Duration.ofDays(30);

	private final DriverSubscriptionRepository subscriptionRepository;
	private final PlanPaymentRepository paymentRepository;
	private final DriverRepository driverRepository;
	private final WaitProofRepository waitProofRepository;
	private final TaxInvoiceRepository taxInvoiceRepository;
	private final Clock clock;

	public DriverPlanResponse getMine(Long userId) {
		return response(current(requireDriver(userId)));
	}

	/** 플랜 고르기. BASIC을 고르면 해지(남은 기간까지 쓴다) */
	public DriverPlanResponse choose(Long userId, DriverPlan plan) {
		DriverSubscription subscription = current(requireDriver(userId));
		if (plan == DriverPlan.BASIC) {
			if (subscription.getPlan().isPaid()) {
				subscription.cancel();
			}
			return response(subscription);
		}
		Instant now = clock.instant();
		if (subscription.planAt(now) == plan && !subscription.isTrial()) {
			subscription.resume();
			return response(subscription);
		}
		subscription.subscribe(plan, now);
		pay(subscription);
		return response(subscription);
	}

	/** 프로 30일 무료 체험 (PLAN-005). 한 번만 */
	public DriverPlanResponse startTrial(Long userId) {
		DriverSubscription subscription = current(requireDriver(userId));
		if (subscription.planAt(clock.instant()).isPaid()) {
			throw new BusinessException(ErrorCode.PLAN_TRIAL_USED, "이미 유료 플랜을 쓰고 있어요.");
		}
		subscription.startTrial(clock.instant());
		return response(subscription);
	}

	/** 해지 취소: 다음 30일도 이어서 쓴다 */
	public DriverPlanResponse resume(Long userId) {
		DriverSubscription subscription = current(requireDriver(userId));
		subscription.resume();
		return response(subscription);
	}

	@Transactional(readOnly = true)
	public DriverPlan planOf(Long userId) {
		Instant now = clock.instant();
		return subscriptionRepository.findById(userId)
				.map(subscription -> subscription.isExpiredAt(now) && !subscription.isTrial() && !subscription.isCancelAtPeriodEnd()
						? subscription.getPlan() // 자동 갱신 대상: 다음에 읽을 때 결제(데모)된다
						: subscription.planAt(now))
				.orElse(DriverPlan.BASIC);
	}

	/** 이 플랜에 없는 기능이면 403 PLAN_403_UPGRADE (PLAN-003 안내 문구) */
	@Transactional(readOnly = true)
	public void requireFeature(Long userId, DriverFeature feature) {
		DriverPlan plan = planOf(userId);
		if (!plan.allows(feature)) {
			throw new BusinessException(ErrorCode.PLAN_UPGRADE_REQUIRED,
					"%s 기능은 프로 플랜부터 쓸 수 있어요. 휴대폰 앱에서 플랜을 바꿔 주세요.".formatted(feature.getLabel()));
		}
	}

	/** 대기 증빙을 주선사에게 한 번 더 보낼 수 있는지 (WAIT-022, 베이직 30일 3건) */
	@Transactional(readOnly = true)
	public void requireWaitProofRoom(Long userId) {
		Integer limit = planOf(userId).getWaitProofLimit();
		if (limit != null && waitProofsSent(userId) >= limit) {
			throw new BusinessException(ErrorCode.PLAN_LIMIT_REACHED,
					"베이직 플랜은 대기 증빙을 30일에 %d건까지 보낼 수 있어요. 프로로 바꾸면 무제한이에요.".formatted(limit));
		}
	}

	/** 세금계산서를 한 장 더 발행할 수 있는지 (LEDG-040, 베이직 월 10장, 프로 월 40장) */
	@Transactional(readOnly = true)
	public void requireTaxInvoiceRoom(Long userId) {
		DriverPlan plan = planOf(userId);
		Integer limit = plan.getTaxInvoiceMonthlyLimit();
		if (limit != null && taxInvoicesThisMonth(userId) >= limit) {
			throw new BusinessException(ErrorCode.PLAN_LIMIT_REACHED, plan == DriverPlan.BASIC
					? "베이직 플랜은 세금계산서를 한 달에 %d장까지 발행할 수 있어요. 프로로 바꾸면 %d장까지예요.".formatted(limit, DriverPlan.PRO.getTaxInvoiceMonthlyLimit())
					: "이번 달 세금계산서 %d장을 다 썼어요. 남은 운행은 다음 달에 주선사별로 묶어 발행해 주세요.".formatted(limit));
		}
	}

	/** 기간이 끝난 구독을 정리하고 돌려준다. 줄이 없으면 BASIC 줄을 만든다 */
	private DriverSubscription current(Driver driver) {
		DriverSubscription subscription = subscriptionRepository.findById(driver.getUserId())
				.orElseGet(() -> subscriptionRepository.save(DriverSubscription.basic(driver)));
		Instant now = clock.instant();
		while (subscription.isExpiredAt(now)) {
			if (subscription.isTrial() || subscription.isCancelAtPeriodEnd()) {
				subscription.downgrade();
			} else {
				subscription.renew();
				pay(subscription);
			}
		}
		return subscription;
	}

	private void pay(DriverSubscription subscription) {
		DriverPlan plan = subscription.getPlan();
		paymentRepository.save(PlanPayment.demo(PlanOwnerType.DRIVER, subscription.getDriverId(), plan.name(), plan.getPrice(),
				subscription.getPeriodStart(), subscription.getPeriodEnd(), clock.instant()));
	}

	private long waitProofsSent(Long userId) {
		return waitProofRepository.countByTripDriverUserIdAndSentAtGreaterThanEqual(userId, clock.instant().minus(WAIT_PROOF_WINDOW));
	}

	private long taxInvoicesThisMonth(Long userId) {
		LocalDate first = KoreanDate.today(clock).withDayOfMonth(1);
		return taxInvoiceRepository.countByDriverUserIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(userId,
				first.atStartOfDay(KoreanDate.ZONE).toInstant(), first.plusMonths(1).atStartOfDay(KoreanDate.ZONE).toInstant());
	}

	private DriverPlanResponse response(DriverSubscription subscription) {
		Long userId = subscription.getDriverId();
		return DriverPlanResponse.of(subscription, clock.instant(), waitProofsSent(userId), taxInvoicesThisMonth(userId));
	}

	private Driver requireDriver(Long userId) {
		return driverRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_DRIVER));
	}
}
