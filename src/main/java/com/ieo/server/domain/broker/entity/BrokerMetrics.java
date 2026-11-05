package com.ieo.server.domain.broker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 주선사 신뢰 지표 (BRK-030~032). 원본은 장부·대기비·대기 기록이고 이 테이블은 캐시다.
 * 차주 오더 마켓이 자주 읽어서 미리 계산해 둔다. 원본이 바뀌면 그 회사를 처음부터 다시 계산한다
 * (BrokerMetricsCalculator). 비율은 0~100 정수(%)다.
 *
 * 우리 회사 오더로 배차된 운행(IEO_DISPATCH)만 센다. 차주가 직접 등록한 운행은 아무 회사나 고를 수 있어서
 * 다른 회사 지표를 깎는 데 쓸 수 있기 때문이다.
 */
@Getter
@Entity
@Table(name = "broker_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerMetrics {

	/** 이보다 끝난 운행이 적으면 지표를 공개하지 않는다 (BRK-032) */
	public static final int MIN_COMPLETED_TRIPS = 20;

	@Id
	private Long brokerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** 평균 지급 소요일 = 운행일 ~ 차주가 받은 날 (인수증 결제만). 받은 건이 없으면 null */
	private Integer avgPaymentDays;

	/** 지급 지연 비율(%) = 지급 예정일이 지난 건 중 늦게 받았거나 아직 못 받은 건 (인수증 결제만). 해당 건이 없으면 null */
	private Integer latePaymentPercent;

	/** 대기비 지급 비율(%) = 지급 예정일이 지난 대기비 중 받은 것. 해당 건이 없으면 null */
	private Integer waitFeePaidPercent;

	/** 이 회사 운행의 평균 대기(분) = 진입 ~ 작업 시작. 기록이 없으면 null */
	private Integer avgWaitMinutes;

	/** 기간 안에 끝난 운행 수. 공개 기준 (BRK-032) */
	@Column(nullable = false)
	private int sampleCount;

	@Column(nullable = false)
	private Instant calculatedAt;

	private BrokerMetrics(Broker broker) {
		this.broker = broker;
	}

	public static BrokerMetrics of(Broker broker) {
		return new BrokerMetrics(broker);
	}

	public void recalculate(int sampleCount, Integer avgPaymentDays, Integer latePaymentPercent,
			Integer waitFeePaidPercent, Integer avgWaitMinutes, Instant now) {
		this.sampleCount = sampleCount;
		this.avgPaymentDays = avgPaymentDays;
		this.latePaymentPercent = latePaymentPercent;
		this.waitFeePaidPercent = waitFeePaidPercent;
		this.avgWaitMinutes = avgWaitMinutes;
		this.calculatedAt = now;
	}

	public boolean isPublished() {
		return sampleCount >= MIN_COMPLETED_TRIPS;
	}
}
