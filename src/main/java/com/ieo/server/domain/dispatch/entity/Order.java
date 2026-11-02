package com.ieo.server.domain.dispatch.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주선사가 올린 오더 하나 (11 배차). 차주가 배차를 요청하고 주선사가 한 명을 확정하면 운행이 생긴다.
 * Ieo는 정보만 제공하고 운송 계약은 주선사와 차주가 맺는다: 확정은 늘 주선사가 한다(자동 확정도 주선사가 고른 방식).
 * 필수 항목이 빠지면 등록되지 않는다 (DSP-002).
 */
@Getter
@Entity
@Table(name = "orders", indexes = {
		@Index(name = "idx_orders_broker", columnList = "broker_id, created_at"),
		@Index(name = "idx_orders_status_pickup", columnList = "status, pickup_until")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

	private static final int MAX_FARE = 100_000_000;
	private static final int MAX_WEIGHT_KG = 50_000;
	private static final int MAX_PAYMENT_DAYS = 365;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** 올린 담당자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by")
	private BrokerMember createdBy;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pickup_site_id")
	private Site pickupSite;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dropoff_site_id")
	private Site dropoffSite;

	/** 상차 가능 시작 */
	@Column(nullable = false)
	private Instant pickupFrom;

	/** 상차 가능 끝. 지나면 마감으로 본다 */
	@Column(nullable = false)
	private Instant pickupUntil;

	/** 하차 마감 */
	@Column(nullable = false)
	private Instant dropoffBy;

	/** 화물 종류. 예: 음료 파레트 */
	@Column(nullable = false, length = 50)
	private String cargoType;

	/** 화물 무게(kg). 트럭 톤수와 다르다 */
	@Column(nullable = false)
	private int weightKg;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Tonnage tonnage;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BodyType bodyType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private LoadingMethod loadingMethod;

	/** 운임(원) */
	@Column(nullable = false)
	private int fare;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PaymentType paymentType;

	/** 하차 후 지급까지 며칠 */
	@Column(nullable = false)
	private int expectedPaymentDays;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private ConsignmentType consignmentType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ConfirmMode confirmMode;

	/** 지정 배차 (DSP-006). 있으면 이 차주만 보고 요청할 수 있다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_driver_id")
	private Driver targetDriver;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private OrderStatus status;

	/** 담당자 여러 명이 동시에 확정하지 못하게 막는다. 늦게 저장한 쪽이 실패한다 */
	@Version
	private Long version;

	@Builder(builderMethodName = "draft", buildMethodName = "create")
	private Order(BrokerMember createdBy, Site pickupSite, Site dropoffSite,
			Instant pickupFrom, Instant pickupUntil, Instant dropoffBy,
			String cargoType, Integer weightKg, Tonnage tonnage, BodyType bodyType, LoadingMethod loadingMethod,
			Integer fare, PaymentType paymentType, Integer expectedPaymentDays,
			ConsignmentType consignmentType, ConfirmMode confirmMode, Driver targetDriver) {
		if (createdBy == null || pickupSite == null || dropoffSite == null
				|| tonnage == null || bodyType == null || loadingMethod == null || paymentType == null
				|| consignmentType == null || cargoType == null || cargoType.isBlank()) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "오더 필수 항목을 모두 입력해 주세요.");
		}
		if (pickupFrom == null || pickupUntil == null || dropoffBy == null
				|| !pickupFrom.isBefore(pickupUntil) || dropoffBy.isBefore(pickupFrom)) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "상차 시간은 시작이 끝보다 앞서고, 하차 마감은 상차 시작 뒤여야 합니다.");
		}
		if (weightKg == null || weightKg < 1 || weightKg > MAX_WEIGHT_KG
				|| fare == null || fare < 0 || fare > MAX_FARE
				|| expectedPaymentDays == null || expectedPaymentDays < 0 || expectedPaymentDays > MAX_PAYMENT_DAYS) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "무게, 운임, 지급 예정일 값을 확인해 주세요.");
		}
		this.broker = createdBy.getBroker();
		this.createdBy = createdBy;
		this.pickupSite = pickupSite;
		this.dropoffSite = dropoffSite;
		this.pickupFrom = pickupFrom;
		this.pickupUntil = pickupUntil;
		this.dropoffBy = dropoffBy;
		this.cargoType = cargoType.strip();
		this.weightKg = weightKg;
		this.tonnage = tonnage;
		this.bodyType = bodyType;
		this.loadingMethod = loadingMethod;
		this.fare = fare;
		this.paymentType = paymentType;
		this.expectedPaymentDays = expectedPaymentDays;
		this.consignmentType = consignmentType;
		this.confirmMode = confirmMode == null ? ConfirmMode.SELECT : confirmMode;
		this.targetDriver = targetDriver;
		this.status = OrderStatus.OPEN;
	}

	/** 모집 중: OPEN이고 상차 가능 시간이 끝나지 않음 */
	public boolean isOpenAt(Instant now) {
		return status == OrderStatus.OPEN && now.isBefore(pickupUntil);
	}

	/**
	 * 이 차주가 이 차량으로 요청할 수 있는지. 지정 배차면 그 차주만.
	 * 차종은 같아야 하고, 톤수는 오더 이상이면 된다 (큰 차로 작은 짐을 나를 수 있다)
	 */
	public void checkRequestable(Driver driver, Vehicle vehicle, Instant now) {
		if (!isOpenAt(now)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_OPEN);
		}
		if (targetDriver != null && !targetDriver.getUserId().equals(driver.getUserId())) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		if (!vehicle.getTonnage().canCarry(tonnage) || vehicle.getBodyType() != bodyType) {
			throw new BusinessException(ErrorCode.DISPATCH_VEHICLE_MISMATCH);
		}
	}

	/** 배차 확정 → 배차 완료 */
	public void assign(Instant now) {
		if (!isOpenAt(now)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_OPEN);
		}
		this.status = OrderStatus.ASSIGNED;
	}

	/** 주선사가 내림. 모집 중일 때만 */
	public void close() {
		if (status != OrderStatus.OPEN) {
			throw new BusinessException(ErrorCode.ORDER_NOT_OPEN);
		}
		this.status = OrderStatus.CLOSED;
	}

	/** 확정된 배차가 취소되어 다시 모집한다. 상차 시간이 이미 지났으면 모집 중으로 보이지 않는다 */
	public void reopen() {
		requireAssigned();
		this.status = OrderStatus.OPEN;
	}

	/** 확정된 배차가 취소되고 오더도 내린다 */
	public void closeAfterCancel() {
		requireAssigned();
		this.status = OrderStatus.CLOSED;
	}

	private void requireAssigned() {
		if (status != OrderStatus.ASSIGNED) {
			throw new BusinessException(ErrorCode.ORDER_NOT_ASSIGNED);
		}
	}

	public boolean belongsTo(Long brokerId) {
		return broker.getId().equals(brokerId);
	}
}
