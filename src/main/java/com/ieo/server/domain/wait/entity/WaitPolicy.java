package com.ieo.server.domain.wait.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.wait.enums.WaitRounding;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주선사별 대기비 정책 (WAIT-010). 예: 무료 2시간, 30분당 1만 원, 상한 5만 원.
 * 고치지 않고 새로 만든다: 지난 대기 기록이 계산에 쓴 정책이 바뀌면 안 된다.
 * 켜진 정책이 주선사당 하나인지는 서비스가 지킨다.
 */
@Getter
@Entity
@Table(name = "wait_policies", indexes = @Index(name = "idx_wait_policies_broker", columnList = "broker_id, active"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitPolicy extends BaseTimeEntity {

	private static final int MAX_MINUTES = 24 * 60;
	private static final int MAX_AMOUNT = 10_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** 무료 대기 시간(분) */
	@Column(nullable = false)
	private int freeMinutes;

	/** 단위 시간(분) */
	@Column(nullable = false)
	private int unitMinutes;

	/** 단위 금액(원) */
	@Column(nullable = false)
	private int unitAmount;

	/** 상한(원). 대기 시간을 부풀려도 이 금액을 넘지 않는다 */
	@Column(nullable = false)
	private int maxAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private WaitRounding rounding;

	@Column(nullable = false)
	private boolean active;

	private WaitPolicy(Broker broker, int freeMinutes, int unitMinutes, int unitAmount, int maxAmount,
			WaitRounding rounding) {
		this.broker = broker;
		this.freeMinutes = freeMinutes;
		this.unitMinutes = unitMinutes;
		this.unitAmount = unitAmount;
		this.maxAmount = maxAmount;
		this.rounding = rounding;
		this.active = true;
	}

	public static WaitPolicy create(Broker broker, int freeMinutes, int unitMinutes, int unitAmount, int maxAmount,
			WaitRounding rounding) {
		if (broker == null || rounding == null
				|| freeMinutes < 0 || freeMinutes > MAX_MINUTES
				|| unitMinutes < 1 || unitMinutes > MAX_MINUTES
				|| unitAmount < 1 || maxAmount < unitAmount || maxAmount > MAX_AMOUNT) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_POLICY);
		}
		return new WaitPolicy(broker, freeMinutes, unitMinutes, unitAmount, maxAmount, rounding);
	}

	/**
	 * 대기비 대상 시간(분)에 대한 청구 가능 금액.
	 * 무료 시간을 넘긴 시간을 단위로 나눠 올림(또는 내림)하고, 단위 금액을 곱한 뒤 상한으로 자른다.
	 */
	public int calculate(long chargeableMinutes) {
		long over = chargeableMinutes - freeMinutes;
		if (over <= 0) {
			return 0;
		}
		long units = rounding == WaitRounding.UP
				? (over + unitMinutes - 1) / unitMinutes
				: over / unitMinutes;
		return (int) Math.min(units * unitAmount, maxAmount);
	}

	/** 새 정책을 켤 때 이전 정책을 끈다 */
	public void deactivate() {
		this.active = false;
	}
}
