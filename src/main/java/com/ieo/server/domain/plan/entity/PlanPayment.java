package com.ieo.server.domain.plan.entity;

import com.ieo.server.domain.plan.enums.PaymentMethod;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 플랜 결제 한 건 (기사님·주선사 공통). 지금은 데모 결제라 돈이 오가지 않지만 기록은 실제처럼 남긴다 (BILL-007).
 * PG를 붙이면 method와 거래 번호를 더한다
 */
@Getter
@Entity
@Table(name = "plan_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanPayment extends BaseTimeEntity {

	/** 플랜 한 번 결제로 쓰는 기간 (기사님·주선사 공통) */
	public static final Duration PERIOD = Duration.ofDays(30);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PlanOwnerType ownerType;

	/** 기사님이면 user id, 주선사면 broker id */
	@Column(nullable = false)
	private Long ownerId;

	/** DriverPlan 또는 BrokerPlan 이름 */
	@Column(nullable = false, length = 20)
	private String plan;

	@Column(nullable = false)
	private int amount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PaymentMethod method;

	@Column(nullable = false)
	private Instant periodStart;

	@Column(nullable = false)
	private Instant periodEnd;

	@Column(nullable = false)
	private Instant paidAt;

	private PlanPayment(PlanOwnerType ownerType, Long ownerId, String plan, int amount, Instant periodStart, Instant periodEnd,
			Instant paidAt) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
		this.plan = plan;
		this.amount = amount;
		this.method = PaymentMethod.DEMO;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.paidAt = paidAt;
	}

	public static PlanPayment demo(PlanOwnerType ownerType, Long ownerId, String plan, int amount, Instant periodStart,
			Instant periodEnd, Instant paidAt) {
		return new PlanPayment(ownerType, ownerId, plan, amount, periodStart, periodEnd, paidAt);
	}
}
