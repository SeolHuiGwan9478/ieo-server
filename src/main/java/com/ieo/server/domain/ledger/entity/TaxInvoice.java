package com.ieo.server.domain.ledger.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.ledger.enums.TaxInvoiceProvider;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 운송료 전자세금계산서 한 장 (LEDG-040). 운행(장부 줄) 1건에 1장, 공급자는 기사님, 받는 사람은 주선사.
 * 발행할 때의 사업자 정보를 그대로 적어 두어, 나중에 상호가 바뀌어도 발행한 계산서는 그대로 보인다.
 * 금액: 공급가액 = 운임 + 대기비, 부가세 = 공급가액의 10%(원 미만 버림). 장부 운임은 부가세 별도로 본다
 */
@Getter
@Entity
@Table(name = "tax_invoices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxInvoice extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ledger_entry_id", nullable = false, unique = true)
	private LedgerEntry ledgerEntry;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id", nullable = false)
	private Broker broker;

	/** 작성일자 = 운행한 날 */
	@Column(nullable = false)
	private LocalDate writeDate;

	@Column(nullable = false, length = 10)
	private String supplierBusinessNumber;

	@Column(nullable = false, length = 100)
	private String supplierName;

	@Column(nullable = false, length = 50)
	private String supplierRepresentative;

	@Column(nullable = false, length = 10)
	private String buyerBusinessNumber;

	@Column(nullable = false, length = 100)
	private String buyerName;

	@Column(length = 50)
	private String buyerRepresentative;

	/** 품목 줄 설명 (구간) */
	@Column(nullable = false, length = 100)
	private String item;

	@Column(nullable = false)
	private int fareAmount;

	@Column(nullable = false)
	private int waitFeeAmount;

	@Column(nullable = false)
	private int supplyAmount;

	@Column(nullable = false)
	private int vat;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private TaxInvoiceProvider provider;

	/** 국세청 승인번호 (데모는 가짜) */
	@Column(nullable = false, length = 30)
	private String approvalNumber;

	@Column(nullable = false)
	private Instant issuedAt;

	private TaxInvoice(TaxInvoiceDraft draft, TaxInvoiceProvider provider, String approvalNumber, Instant issuedAt) {
		this.driver = draft.entry().getDriver();
		this.ledgerEntry = draft.entry();
		this.broker = draft.entry().getBroker();
		this.writeDate = draft.entry().getWorkDate();
		this.supplierBusinessNumber = draft.supplierBusinessNumber();
		this.supplierName = draft.supplierName();
		this.supplierRepresentative = draft.supplierRepresentative();
		this.buyerBusinessNumber = draft.buyerBusinessNumber();
		this.buyerName = draft.buyerName();
		this.buyerRepresentative = draft.buyerRepresentative();
		this.item = draft.item();
		this.fareAmount = draft.fareAmount();
		this.waitFeeAmount = draft.waitFeeAmount();
		this.supplyAmount = draft.supplyAmount();
		this.vat = draft.vat();
		this.provider = provider;
		this.approvalNumber = approvalNumber;
		this.issuedAt = issuedAt;
	}

	public static TaxInvoice issued(TaxInvoiceDraft draft, TaxInvoiceProvider provider, String approvalNumber, Instant issuedAt) {
		return new TaxInvoice(draft, provider, approvalNumber, issuedAt);
	}

	public int totalAmount() {
		return supplyAmount + vat;
	}
}
