package com.ieo.server.domain.trip.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 운행 하나 (01 운행). 배차 1건을 등록부터 하차 완료까지 추적한다.
 * 대기·인수증·장부·배차 추천이 모두 운행을 기준으로 붙는다.
 * 상하차지는 TripStop으로 상차 1곳, 하차 1곳을 함께 만든다.
 * 직접 등록(create)과 Ieo 배차 확정(fromDispatch, TRIP-004) 두 경로로 생긴다.
 */
@Getter
@Entity
@Table(name = "trips", indexes = @Index(name = "idx_trips_driver_status", columnList = "driver_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

	private static final int MAX_FARE = 100_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	/** Ieo 배차로 생긴 운행이면 그 오더. 배차가 취소되고 다시 확정되면 한 오더에 운행이 여럿 생긴다(끝나지 않은 것은 하나) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Order order;

	/** Ieo에 있는 주선사. 대기비 정책을 찾을 때 쓴다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** Ieo에 없는 주선사면 이름만 적는다. broker가 있으면 비운다 */
	@Column(length = 50)
	private String brokerName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private TripStatus status;

	/** 운임 (원) */
	@Column(nullable = false)
	private int fare;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PaymentType paymentType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private TripSource source;

	private Instant startedAt;

	private Instant completedAt;

	private Instant canceledAt;

	/** 주선사·수하인에게 ETA 알림을 보낼지 (TRIP-024) */
	@Column(nullable = false)
	private boolean etaNoticeEnabled;

	/** 주선사에게 마지막으로 알린 도착 예정 시각과 알린 때. 변경 재알림을 가르는 기준이다 (TRIP-022) */
	private Instant notifiedEta;

	private Instant etaSharedAt;

	@OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TripStop> stops = new ArrayList<>();

	private Trip(Driver driver, Vehicle vehicle, Broker broker, String brokerName, int fare,
			PaymentType paymentType, TripSource source) {
		this.driver = driver;
		this.vehicle = vehicle;
		this.broker = broker;
		this.brokerName = brokerName;
		this.status = TripStatus.SCHEDULED;
		this.fare = fare;
		this.paymentType = paymentType;
		this.source = source;
		this.etaNoticeEnabled = true;
	}

	/** 상차지·하차지와 함께 예약 상태로 만든다. 차량은 이 차주의 차량이어야 한다 */
	public void markEtaShared(Instant eta, Instant now) {
		this.notifiedEta = eta;
		this.etaSharedAt = now;
	}

	public static Trip create(Driver driver, Vehicle vehicle, Broker broker, String brokerName,
			int fare, PaymentType paymentType, TripSource source,
			Site pickupSite, Instant pickupPlannedAt, Site dropoffSite, Instant dropoffPlannedAt) {
		if (!vehicle.isOwnedBy(driver.getUserId())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND);
		}
		if (fare < 0 || fare > MAX_FARE) {
			throw new BusinessException(ErrorCode.TRIP_INVALID_FARE);
		}
		if (paymentType == null || source == null || pickupSite == null || dropoffSite == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String name = broker != null || brokerName == null || brokerName.isBlank() ? null : brokerName.strip();
		Trip trip = new Trip(driver, vehicle, broker, name, fare, paymentType, source);
		trip.stops.add(new TripStop(trip, StopType.PICKUP, pickupSite, pickupPlannedAt));
		trip.stops.add(new TripStop(trip, StopType.DROPOFF, dropoffSite, dropoffPlannedAt));
		return trip;
	}

	/**
	 * 배차 확정으로 운행을 만든다 (TRIP-004). 오더 내용을 그대로 옮기고,
	 * 상차 예정은 상차 가능 시작, 하차 예정은 하차 마감으로 둔다
	 */
	public static Trip fromDispatch(DispatchRequest request) {
		Order order = request.getOrder();
		Trip trip = create(request.getDriver(), request.getVehicle(), order.getBroker(), null,
				order.getFare(), order.getPaymentType(), TripSource.IEO_DISPATCH,
				order.getPickupSite(), order.getPickupFrom(), order.getDropoffSite(), order.getDropoffBy());
		trip.order = order;
		return trip;
	}

	public TripStop pickup() {
		return stop(StopType.PICKUP);
	}

	public TripStop dropoff() {
		return stop(StopType.DROPOFF);
	}

	/** 출발: 상차지로 이동 */
	public void start(Instant now) {
		moveTo(TripStatus.TO_PICKUP);
		this.startedAt = now;
	}

	/** 상차지 진입. 진입·이탈 시각은 대기 기록이 가진다 */
	public void arriveAtPickup() {
		moveTo(TripStatus.AT_PICKUP);
	}

	/** 상차를 마치고 상차지 이탈 → 운송 중 */
	public void departPickup() {
		moveTo(TripStatus.IN_TRANSIT);
	}

	/** 하차지 진입 */
	public void arriveAtDropoff() {
		moveTo(TripStatus.AT_DROPOFF);
	}

	/** 하차 완료 (인수 서명 또는 하차지 이탈) */
	public void complete(Instant now) {
		moveTo(TripStatus.COMPLETED);
		this.completedAt = now;
	}

	public void cancel(Instant now) {
		moveTo(TripStatus.CANCELLED);
		this.canceledAt = now;
	}

	public void changeEtaNotice(boolean enabled) {
		this.etaNoticeEnabled = enabled;
	}

	public boolean isOwnedBy(Long userId) {
		return driver.getUserId().equals(userId);
	}

	private void moveTo(TripStatus next) {
		if (!status.canMoveTo(next)) {
			throw new BusinessException(ErrorCode.TRIP_INVALID_STATUS,
					"지금 운행 상태(%s)에서는 %s(으)로 바꿀 수 없습니다.".formatted(status, next));
		}
		this.status = next;
	}

	private TripStop stop(StopType type) {
		return stops.stream()
				.filter(stop -> stop.getType() == type)
				.findFirst()
				.orElseThrow();
	}
}
