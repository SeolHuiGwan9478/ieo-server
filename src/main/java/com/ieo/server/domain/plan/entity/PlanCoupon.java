package com.ieo.server.domain.plan.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보상 쿠폰 한 장 (DSP-034). 지금은 주선사가 상차 2시간 안에 배차를 취소했을 때만 준다.
 * 받으면 바로 쓰이지 않고, 기사님이 [사용하기]를 눌러야 프로 이용일이 더해진다. 90일 안에 써야 한다.
 * 운행 하나에 한 장만 주도록 tripId를 적는다. 결제가 아니라 plan_payments에는 남기지 않는다
 */
@Getter
@Entity
@Table(name = "plan_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanCoupon extends BaseTimeEntity {

	/** 받은 날부터 쓸 수 있는 기간 (가정값) */
	public static final Duration VALID_FOR = Duration.ofDays(90);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	/** 쿠폰을 준 이유가 된 운행 */
	@Column(nullable = false, unique = true)
	private Long tripId;

	/** 쓰면 더해지는 프로 이용일 */
	@Column(nullable = false)
	private int days;

	/** 화면에 보이는 이유 ("주선사 늦은 취소") */
	@Column(nullable = false, length = 100)
	private String reason;

	@Column(nullable = false)
	private Instant issuedAt;

	@Column(nullable = false)
	private Instant expiresAt;

	/** 쓴 시각. 안 썼으면 null */
	private Instant usedAt;

	private PlanCoupon(Driver driver, Long tripId, int days, String reason, Instant issuedAt) {
		this.driver = driver;
		this.tripId = tripId;
		this.days = days;
		this.reason = reason;
		this.issuedAt = issuedAt;
		this.expiresAt = issuedAt.plus(VALID_FOR);
	}

	public static PlanCoupon issue(Driver driver, Long tripId, int days, String reason, Instant now) {
		return new PlanCoupon(driver, tripId, days, reason, now);
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void use(Instant now) {
		if (usedAt != null) {
			throw new BusinessException(ErrorCode.COUPON_USED);
		}
		if (!now.isBefore(expiresAt)) {
			throw new BusinessException(ErrorCode.COUPON_EXPIRED);
		}
		this.usedAt = now;
	}
}
