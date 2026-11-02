package com.ieo.server.domain.ledger.entity;

import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장부 줄의 부가 수입: 대기비 (LEDG-002, WAIT-024). 대기 기록 하나에 하나.
 * 금액은 장부를 만드는 순간의 청구 가능 대기비를 복사해 둔다(장부는 그때의 기록).
 * 상차·하차 어느 쪽인지, 증빙 링크는 waitRecord로 따라간다. paidAt이 비어 있으면 청구 예정.
 */
@Getter
@Entity
@Table(name = "extra_incomes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtraIncome extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ledger_entry_id")
	private LedgerEntry ledgerEntry;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wait_record_id", unique = true)
	private WaitRecord waitRecord;

	@Column(nullable = false)
	private int amount;

	/** 받은 시각. 비어 있으면 청구 예정 */
	private Instant paidAt;

	private ExtraIncome(LedgerEntry ledgerEntry, WaitRecord waitRecord, int amount) {
		this.ledgerEntry = ledgerEntry;
		this.waitRecord = waitRecord;
		this.amount = amount;
	}

	/** 대기비가 0원이거나 계산되지 않은 기록은 서비스가 거른다 */
	public static ExtraIncome waitFee(LedgerEntry ledgerEntry, WaitRecord waitRecord, int amount) {
		return new ExtraIncome(ledgerEntry, waitRecord, amount);
	}

	public void markPaid(Instant now) {
		this.paidAt = now;
	}

	public void unmarkPaid() {
		this.paidAt = null;
	}

	public boolean isPaid() {
		return paidAt != null;
	}
}
