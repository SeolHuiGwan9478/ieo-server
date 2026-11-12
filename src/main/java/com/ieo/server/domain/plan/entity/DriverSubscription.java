package com.ieo.server.domain.plan.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.plan.enums.DriverPlan;
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
 * 기사님의 지금 플랜 (PLAN-001, 005, 008). 기사님당 한 줄, 줄이 없으면 BASIC.
 * 유료 플랜은 30일 기간을 가진다. 기간이 끝나면 서비스가 다음 30일을 결제(데모)하거나, 체험·해지면 BASIC으로 내린다
 */
@Getter
@Entity
@Table(name = "driver_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverSubscription extends BaseTimeEntity {

	@Id
	private Long driverId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private DriverPlan plan;

	/** 유료 플랜의 지금 기간. BASIC이면 null */
	private Instant periodStart;

	private Instant periodEnd;

	/** 프로 무료 체험 중 (PLAN-005). 체험이 끝나면 결제 없이 BASIC으로 내린다 */
	@Column(nullable = false)
	private boolean trial;

	/** 체험은 한 번만 */
	@Column(nullable = false)
	private boolean trialUsed;

	/** 해지함: 남은 기간까지 쓰고 BASIC으로 (BILL-004) */
	@Column(nullable = false)
	private boolean cancelAtPeriodEnd;

	private DriverSubscription(Driver driver) {
		this.driver = driver;
		this.plan = DriverPlan.BASIC;
	}

	public static DriverSubscription basic(Driver driver) {
		return new DriverSubscription(driver);
	}

	/** 기간이 끝났으면 BASIC으로 본다. 갱신은 서비스가 먼저 해 둔다 */
	public DriverPlan planAt(Instant now) {
		if (!plan.isPaid() || periodEnd == null || !now.isBefore(periodEnd)) {
			return DriverPlan.BASIC;
		}
		return plan;
	}

	public boolean isExpiredAt(Instant now) {
		return plan.isPaid() && periodEnd != null && !now.isBefore(periodEnd);
	}

	/** 유료 플랜을 고른다. 새 30일이 지금부터 시작한다 */
	public void subscribe(DriverPlan plan, Instant now) {
		if (plan == null || !plan.isPaid()) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		this.plan = plan;
		this.periodStart = now;
		this.periodEnd = now.plus(PlanPayment.PERIOD);
		this.trial = false;
		this.cancelAtPeriodEnd = false;
	}

	public void startTrial(Instant now) {
		if (trialUsed) {
			throw new BusinessException(ErrorCode.PLAN_TRIAL_USED);
		}
		subscribe(DriverPlan.PRO, now);
		this.trial = true;
		this.trialUsed = true;
	}

	/** 끝난 기간 바로 다음 30일 */
	public void renew() {
		this.periodStart = periodEnd;
		this.periodEnd = periodEnd.plus(PlanPayment.PERIOD);
	}

	public void cancel() {
		if (!plan.isPaid()) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		this.cancelAtPeriodEnd = true;
	}

	public void resume() {
		this.cancelAtPeriodEnd = false;
	}

	public void downgrade() {
		this.plan = DriverPlan.BASIC;
		this.periodStart = null;
		this.periodEnd = null;
		this.trial = false;
		this.cancelAtPeriodEnd = false;
	}
}
