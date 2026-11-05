package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.BrokerPlan;
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
 * 주선사의 현재 플랜 (12장 PLAN-001). 회사당 한 줄. 줄이 없으면 BASIC이다.
 * 결제(BILL-*)가 붙기 전이라 상태·결제 기간은 없고, 플랜을 바꾸면 바로 적용된다 (BILL-007 테스트 모드).
 * 결제가 붙으면 결제 성공 뒤에 change를 부르고, 기간·상태 컬럼을 더한다
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

	private BrokerSubscription(Broker broker, BrokerPlan plan, Instant now) {
		this.broker = broker;
		this.plan = plan;
		this.startedAt = now;
	}

	public static BrokerSubscription of(Broker broker, BrokerPlan plan, Instant now) {
		if (plan == null) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		return new BrokerSubscription(broker, plan, now);
	}

	/** 같은 플랜이면 아무것도 안 한다 (시작 시각 유지) */
	public void change(BrokerPlan plan, Instant now) {
		if (plan == null) {
			throw new BusinessException(ErrorCode.PLAN_INVALID);
		}
		if (this.plan == plan) {
			return;
		}
		this.plan = plan;
		this.startedAt = now;
	}
}
