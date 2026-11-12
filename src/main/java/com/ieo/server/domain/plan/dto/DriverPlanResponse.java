package com.ieo.server.domain.plan.dto;

import com.ieo.server.domain.plan.entity.DriverSubscription;
import com.ieo.server.domain.plan.enums.DriverFeature;
import com.ieo.server.domain.plan.enums.DriverPlan;
import com.ieo.server.global.util.KoreanDate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 내 플랜 (PLAN-002, 007). 앱은 이걸로 티켓을 그리고 잠금 안내를 한다.
 * paidUntil은 유료 기간이 끝나는 날(한국 날짜, 그 시각까지 쓴다), daysLeft는 오늘부터 그날까지 남은 날. 베이직이면 둘 다 null.
 * renews: 기간이 끝나면 같은 플랜으로 다시 결제(데모)되는지. 체험·해지면 false.
 * 한도가 null이면 무제한. demoPayment는 결제가 시연용임을 화면에 알리라는 뜻
 */
public record DriverPlanResponse(
		DriverPlan plan,
		String planLabel,
		int price,
		boolean trial,
		boolean trialAvailable,
		boolean renews,
		LocalDate paidUntil,
		Integer daysLeft,
		List<DriverFeature> features,
		Usage usage,
		List<Option> plans,
		boolean demoPayment) {

	public record Usage(long waitProofsSent, Integer waitProofLimit, long taxInvoicesThisMonth, Integer taxInvoiceLimit) {
	}

	public record Option(DriverPlan code, String label, int price, List<String> highlights) {
	}

	public static DriverPlanResponse of(DriverSubscription subscription, Instant now, long waitProofsSent, long taxInvoices) {
		DriverPlan plan = subscription.planAt(now);
		boolean paid = plan.isPaid();
		LocalDate paidUntil = paid ? KoreanDate.of(subscription.getPeriodEnd()) : null;
		Integer daysLeft = paid ? (int) ChronoUnit.DAYS.between(KoreanDate.of(now), paidUntil) : null;
		return new DriverPlanResponse(
				plan,
				plan.getLabel(),
				plan.getPrice(),
				paid && subscription.isTrial(),
				!subscription.isTrialUsed(),
				paid && !subscription.isTrial() && !subscription.isCancelAtPeriodEnd(),
				paidUntil,
				daysLeft,
				plan.getFeatures().stream().sorted().toList(),
				new Usage(waitProofsSent, plan.getWaitProofLimit(), taxInvoices, plan.getTaxInvoiceMonthlyLimit()),
				Arrays.stream(DriverPlan.values()).map(option -> new Option(option, option.getLabel(), option.getPrice(), option.getHighlights())).toList(),
				true);
	}
}
