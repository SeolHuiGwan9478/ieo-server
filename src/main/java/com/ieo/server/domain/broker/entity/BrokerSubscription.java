package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주선사의 현재 플랜 (12장 PLAN-001, PLAN-008, BILL-004). 회사당 한 줄. 줄이 없으면 BASIC이다.
 * 2026-11-14: 기사님 플랜처럼 30일 기간을 가진다. 프로를 고르면 지금부터 30일, 기간이 끝나면 다음 30일을 결제(데모)한다.
 * 해지(베이직 고르기)하면 남은 날까지 프로를 쓰고 기간이 끝나면 베이직으로 내린다. 체험·쿠폰은 없다
 */
@Getter
@Entity
@Table(name = "broker_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerSubscription extends BaseTimeEntity {

	@Id
	private Long brokerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BrokerPlan plan;

	/** 지금 플랜을 시작한 시각 */
	@Column(nullable = false)
	private Instant startedAt;

	/** 유료 플랜의 지금 30일. BASIC이면 null */
	private Instant periodStart;

	private Instant periodEnd;

	/** 해지함: 남은 기간까지 쓰고 BASIC으로 */
	@Column(nullable = false)
	private boolean cancelAtPeriodEnd;

	private BrokerSubscription(Broker broker, Instant now) {
		this.broker = broker;
		this.plan = BrokerPlan.BASIC;
		this.startedAt = now;
	}

	public static BrokerSubscription basic(Broker broker, Instant now) {
		return new BrokerSubscription(broker, now);
	}

	/** 기간이 끝났으면 BASIC으로 본다. 갱신은 서비스가 먼저 해 둔다 */
	public BrokerPlan planAt(Instant now) {
		if (plan.getPrice() == 0 || periodEnd == null || !now.isBefore(periodEnd)) {
			return BrokerPlan.BASIC;
		}
		return plan;
	}

	public boolean isExpiredAt(Instant now) {
		return plan.getPrice() > 0 && periodEnd != null && !now.isBefore(periodEnd);
	}

	/** 유료 플랜을 고른다. 새 30일이 지금부터 시작한다 */
	public void subscribe(BrokerPlan plan, Instant now) {
		if (plan == null || plan.getPrice() == 0) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		this.plan = plan;
		this.startedAt = now;
		this.periodStart = now;
		this.periodEnd = now.plus(PlanPayment.PERIOD);
		this.cancelAtPeriodEnd = false;
	}

	/** 끝난 기간 바로 다음 30일 */
	public void renew() {
		this.periodStart = periodEnd;
		this.periodEnd = periodEnd.plus(PlanPayment.PERIOD);
	}

	public void cancel() {
		if (plan.getPrice() == 0) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		this.cancelAtPeriodEnd = true;
	}

	public void resume() {
		this.cancelAtPeriodEnd = false;
	}

	public void downgrade(Instant now) {
		this.plan = BrokerPlan.BASIC;
		this.startedAt = now;
		this.periodStart = null;
		this.periodEnd = null;
		this.cancelAtPeriodEnd = false;
	}
}
