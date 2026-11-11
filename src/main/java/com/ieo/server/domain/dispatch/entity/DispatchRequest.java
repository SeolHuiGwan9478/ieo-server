package com.ieo.server.domain.dispatch.entity;

import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.vehicle.entity.Vehicle;
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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주가 오더에 보낸 배차 요청 (DSP-020). 철회했거나 만료된 오더는 다시 요청할 수 있어, 그때마다 줄이 새로 생긴다.
 * 같은 오더에 대기 중 요청은 하나뿐이다 (scripts/dispatch-requests-pending-unique.sql).
 * 요청 후 10분 안에 확정되지 않으면 만료로 본다(createdAt으로 판단, DSP-023). 다시 요청할 때만 EXPIRED로 저장한다
 */
@Getter
@Entity
@Table(name = "dispatch_requests",
		indexes = {
				@Index(name = "idx_dispatch_requests_driver_status", columnList = "driver_id, status"),
				@Index(name = "idx_dispatch_requests_order_driver", columnList = "order_id, driver_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispatchRequest extends BaseTimeEntity {

	public static final Duration TTL = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	/** 이 오더에 쓸 차량. 주선사는 이 차량 정보를 보고 고른다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private DispatchRequestStatus status;

	/** 차량 앱의 배차 추천(정차 중 근처 Top 3)에서 눌러 보낸 요청 (REC-003). 추천이 얼마나 쓰이는지 보는 데 쓴다 */
	@Column(nullable = false)
	private boolean isRecommendation;

	private DispatchRequest(Order order, Driver driver, Vehicle vehicle, boolean isRecommendation) {
		this.order = order;
		this.driver = driver;
		this.vehicle = vehicle;
		this.status = DispatchRequestStatus.PENDING;
		this.isRecommendation = isRecommendation;
	}

	/** 요청할 수 있는지는 Order.checkRequestable과 서비스(동시 요청 3건)가 먼저 확인한다 */
	public static DispatchRequest create(Order order, Driver driver, Vehicle vehicle) {
		return create(order, driver, vehicle, false);
	}

	public static DispatchRequest create(Order order, Driver driver, Vehicle vehicle, boolean isRecommendation) {
		return new DispatchRequest(order, driver, vehicle, isRecommendation);
	}

	/** 만료 시각. 저장 전에는 createdAt이 없어 null */
	public Instant expiresAt() {
		return getCreatedAt() == null ? null : getCreatedAt().plus(TTL);
	}

	public DispatchRequestStatus statusAt(Instant now) {
		Instant expiresAt = expiresAt();
		if (status == DispatchRequestStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt)) {
			return DispatchRequestStatus.EXPIRED;
		}
		return status;
	}

	/** 주선사 확정 (자동 확정 포함) */
	public void confirm(Instant now) {
		requirePending(now);
		this.status = DispatchRequestStatus.CONFIRMED;
	}

	/**
	 * 다른 차주가 확정되었거나 오더를 내려 마감. 이미 끝난 요청은 그대로 둔다.
	 * 아직 기다리던 요청(만료 전)이었으면 true: 차주에게 마감을 알린다
	 */
	public boolean decline(Instant now) {
		if (status != DispatchRequestStatus.PENDING) {
			return false;
		}
		boolean waiting = statusAt(now) == DispatchRequestStatus.PENDING;
		this.status = DispatchRequestStatus.DECLINED;
		return waiting;
	}

	/** 차주가 철회 */
	public void withdraw(Instant now) {
		requirePending(now);
		this.status = DispatchRequestStatus.WITHDRAWN;
	}

	/** 이 요청이 있어도 같은 오더를 다시 요청할 수 있는지. 철회했거나 만료된 요청만 */
	public boolean allowsRetry(Instant now) {
		DispatchRequestStatus current = statusAt(now);
		return current == DispatchRequestStatus.WITHDRAWN || current == DispatchRequestStatus.EXPIRED;
	}

	/** 다시 요청하기 전에, 시각으로만 만료였던 요청을 EXPIRED로 남긴다. 대기 중 요청은 오더마다 하나뿐이어야 해서 */
	public void settleExpiry(Instant now) {
		if (status == DispatchRequestStatus.PENDING && statusAt(now) == DispatchRequestStatus.EXPIRED) {
			this.status = DispatchRequestStatus.EXPIRED;
		}
	}

	/** 확정된 배차를 취소. 확정된 요청만 */
	public void cancel() {
		if (status != DispatchRequestStatus.CONFIRMED) {
			throw new BusinessException(ErrorCode.DISPATCH_REQUEST_NOT_PENDING);
		}
		this.status = DispatchRequestStatus.CANCELED;
	}

	public boolean isRequestedBy(Long userId) {
		return driver.getUserId().equals(userId);
	}

	private void requirePending(Instant now) {
		if (statusAt(now) != DispatchRequestStatus.PENDING) {
			throw new BusinessException(ErrorCode.DISPATCH_REQUEST_NOT_PENDING);
		}
	}
}
