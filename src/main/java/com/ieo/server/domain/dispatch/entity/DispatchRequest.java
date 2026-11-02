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
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주가 오더에 보낸 배차 요청 (DSP-020). 같은 오더에 한 번만 요청할 수 있다.
 * 요청 후 10분 안에 확정되지 않으면 만료로 본다(저장하지 않고 createdAt으로 판단, DSP-023).
 */
@Getter
@Entity
@Table(name = "dispatch_requests",
		uniqueConstraints = @UniqueConstraint(name = "uk_dispatch_requests_order_driver", columnNames = {"order_id", "driver_id"}),
		indexes = @Index(name = "idx_dispatch_requests_driver_status", columnList = "driver_id, status"))
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

	private DispatchRequest(Order order, Driver driver, Vehicle vehicle) {
		this.order = order;
		this.driver = driver;
		this.vehicle = vehicle;
		this.status = DispatchRequestStatus.PENDING;
	}

	/** 요청할 수 있는지는 Order.checkRequestable과 서비스(동시 요청 3건)가 먼저 확인한다 */
	public static DispatchRequest create(Order order, Driver driver, Vehicle vehicle) {
		return new DispatchRequest(order, driver, vehicle);
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

	/** 다른 차주가 확정되어 마감. 이미 끝난 요청은 그대로 둔다 */
	public void decline() {
		if (status == DispatchRequestStatus.PENDING) {
			this.status = DispatchRequestStatus.DECLINED;
		}
	}

	/** 차주가 철회 */
	public void withdraw(Instant now) {
		requirePending(now);
		this.status = DispatchRequestStatus.WITHDRAWN;
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
