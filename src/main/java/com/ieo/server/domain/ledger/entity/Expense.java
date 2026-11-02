package com.ieo.server.domain.ledger.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import com.ieo.server.domain.trip.entity.Trip;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 경비 (LEDG-010, 011): "주유 15만 원", "통행료 2만 3천 원".
 * 그때 진행 중이던 운행에 붙여 두면 운행별 실수익을 계산할 수 있다.
 */
@Getter
@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expenses_driver_date", columnList = "driver_id, spent_on"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends BaseTimeEntity {

	private static final int MAX_AMOUNT = 10_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	/** 그때 진행 중이던 운행 (선택) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_id")
	private Trip trip;

	/** 쓴 날 (한국 날짜) */
	@Column(nullable = false)
	private LocalDate spentOn;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private ExpenseCategory category;

	@Column(nullable = false)
	private int amount;

	@Column(length = 100)
	private String memo;

	private Expense(Driver driver, Trip trip, LocalDate spentOn, ExpenseCategory category, int amount, String memo) {
		this.driver = driver;
		this.trip = trip;
		this.spentOn = spentOn;
		this.category = category;
		this.amount = amount;
		this.memo = memo;
	}

	public static Expense create(Driver driver, Trip trip, LocalDate spentOn, ExpenseCategory category, int amount,
			String memo) {
		Expense expense = new Expense(driver, trip, null, null, 0, null);
		expense.change(spentOn, category, amount, memo);
		return expense;
	}

	public void change(LocalDate spentOn, ExpenseCategory category, int amount, String memo) {
		if (spentOn == null || category == null) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID);
		}
		if (amount < 1 || amount > MAX_AMOUNT) {
			throw new BusinessException(ErrorCode.LEDGER_INVALID, "경비는 1원에서 1천만 원 사이로 입력해 주세요.");
		}
		this.spentOn = spentOn;
		this.category = category;
		this.amount = amount;
		this.memo = memo == null || memo.isBlank() ? null : memo.strip();
	}

	public boolean isOwnedBy(Long userId) {
		return driver.getUserId().equals(userId);
	}
}
