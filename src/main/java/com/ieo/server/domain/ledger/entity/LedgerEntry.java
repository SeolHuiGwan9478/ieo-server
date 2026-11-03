package com.ieo.server.domain.ledger.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 장부 한 줄: 운행 한 건의 수입 (06 장부). 운행이 끝나면 자동으로 생기고(LEDG-001), 직접 추가할 수도 있다(LEDG-004).
 * 장부는 그때의 기록이라 운임·결제 방식·설명은 만드는 순간 복사해 둔다. 운행이나 상하차지가 나중에 바뀌어도 장부는 그대로다.
 * paidAt이 비어 있으면 받을 돈(미수금)이다 (LEDG-021).
 */
@Getter
@Entity
@Table(name = "ledger_entries", indexes = {
		@Index(name = "idx_ledger_entries_driver_date", columnList = "driver_id, work_date"),
		@Index(name = "idx_ledger_entries_driver_unpaid", columnList = "driver_id, paid_at, expected_payment_date")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry extends BaseTimeEntity {

	/** 주선사 정산 주기를 모를 때 (LEDG-020) */
	public static final int DEFAULT_SETTLEMENT_DAYS = 30;
	private static final int MAX_FARE = 100_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	/** 자동으로 생긴 줄이면 그 운행. 운행 하나에 장부 한 줄 */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_id", unique = true)
	private Trip trip;

	/** 운행일 (한국 날짜) */
	@Column(nullable = false)
	private LocalDate workDate;

	/** 예: 평택 포승 물류센터 → 대구 성서 공단 */
	@Column(nullable = false, length = 100)
	private String description;

	@Column(nullable = false)
	private int fare;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private PaymentType paymentType;

	/** Ieo에 있는 주선사. 주선사별 미수금 집계에 쓴다 (LEDG-023) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** Ieo에 없는 주선사 이름. broker가 있으면 비운다 */
	@Column(length = 50)
	private String brokerName;

	/** 주행거리(km). 차량 앱이 Pleos 주행거리계로 보낸다 */
	private Integer distanceKm;

	@Column(nullable = false)
	private LocalDate expectedPaymentDate;

	/** 받은 시각. 비어 있으면 미수금 */
	private Instant paidAt;

	private LedgerEntry(Driver driver, Trip trip, LocalDate workDate, String description, int fare,
			PaymentType paymentType, Broker broker, String brokerName, LocalDate expectedPaymentDate) {
		this.driver = driver;
		this.trip = trip;
		this.workDate = workDate;
		this.description = description;
		this.fare = fare;
		this.paymentType = paymentType;
		this.broker = broker;
		this.brokerName = broker == null ? brokerName : null;
		this.expectedPaymentDate = expectedPaymentDate;
	}

	/**
	 * 끝난 운행으로 장부 한 줄을 만든다. 지급 예정일은 결제 방식과 정산 일수로 정한다.
	 * settlementDays: 오더의 지급 예정일 → 주선사 정산 주기 → 30일 순서로 서비스가 고른다
	 */
	public static LedgerEntry fromTrip(Trip trip, LocalDate workDate, int settlementDays) {
		String description = trip.pickup().getSite().getName() + " → " + trip.dropoff().getSite().getName();
		return new LedgerEntry(trip.getDriver(), trip, workDate, truncate(description), trip.getFare(),
				trip.getPaymentType(), trip.getBroker(), trip.getBrokerName(),
				expectedPaymentDate(trip.getPaymentType(), workDate, settlementDays));
	}

	/** Ieo로 등록하지 않은 운행을 직접 추가한다 (LEDG-004) */
	public static LedgerEntry manual(Driver driver, LocalDate workDate, String description, int fare,
			PaymentType paymentType, String brokerName, LocalDate expectedPaymentDate) {
		if (workDate == null || paymentType == null || description == null || description.isBlank()) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID);
		}
		validateFare(fare);
		String name = brokerName == null || brokerName.isBlank() ? null : brokerName.strip();
		LocalDate expected = expectedPaymentDate != null ? expectedPaymentDate
				: expectedPaymentDate(paymentType, workDate, DEFAULT_SETTLEMENT_DAYS);
		return new LedgerEntry(driver, null, workDate, truncate(description.strip()), fare, paymentType, null, name,
				expected);
	}

	/**
	 * 지급 예정일 (LEDG-020): 선불·착불은 운행일, 인수증은 운행일 + 정산 일수.
	 * 전자인수증이 서명되면 rebaseOnDeliveryProof로 서명일 기준으로 다시 잡는다
	 */
	public static LocalDate expectedPaymentDate(PaymentType paymentType, LocalDate workDate, int settlementDays) {
		return paymentType == PaymentType.RECEIPT ? workDate.plusDays(settlementDays) : workDate;
	}

	/** 운임·결제 방식·지급 예정일 고치기 (LEDG-003) */
	public void edit(int fare, PaymentType paymentType, LocalDate expectedPaymentDate) {
		validateFare(fare);
		if (paymentType == null || expectedPaymentDate == null) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID);
		}
		this.fare = fare;
		this.paymentType = paymentType;
		this.expectedPaymentDate = expectedPaymentDate;
	}

	/** 인수증 결제는 인수증 전달일부터 정산 일수를 센다 (LEDG-020, POD-008) */
	public void rebaseOnDeliveryProof(LocalDate deliveredOn, int settlementDays) {
		if (paymentType == PaymentType.RECEIPT && paidAt == null) {
			this.expectedPaymentDate = deliveredOn.plusDays(settlementDays);
		}
	}

	public void changeDistance(Integer distanceKm) {
		if (distanceKm != null && (distanceKm < 0 || distanceKm > 3000)) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID, "주행거리는 0km에서 3,000km 사이여야 합니다.");
		}
		this.distanceKm = distanceKm;
	}

	public void markPaid(Instant now) {
		this.paidAt = now;
	}

	/** 잘못 누른 "받음"을 되돌린다 */
	public void unmarkPaid() {
		this.paidAt = null;
	}

	public boolean isPaid() {
		return paidAt != null;
	}

	/** 지급 예정일이 지났는데 받지 못함 (LEDG-022) */
	public boolean isOverdue(LocalDate today) {
		return paidAt == null && today.isAfter(expectedPaymentDate);
	}

	public boolean isOwnedBy(Long userId) {
		return driver.getUserId().equals(userId);
	}

	/** 표시용 주선사 이름 */
	public String brokerDisplayName() {
		return broker != null ? broker.getName() : brokerName;
	}

	private static void validateFare(int fare) {
		if (fare < 0 || fare > MAX_FARE) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID, "운임은 0원에서 1억 원 사이로 입력해 주세요.");
		}
	}

	private static String truncate(String text) {
		return text.length() > 100 ? text.substring(0, 100) : text;
	}
}
