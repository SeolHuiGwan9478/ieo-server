package com.ieo.server.domain.broker.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주선사(운송주선업체) 회사. 담당자 계정은 broker_members로 연결한다. (요구사항: docs/requirements/07_주선사.md)
 */
@Getter
@Entity
@Table(name = "brokers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Broker extends BaseTimeEntity {

	private static final int DEFAULT_SETTLEMENT_DAYS = 30;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 상호 */
	@Column(nullable = false, length = 50)
	private String name;

	/** 사업자등록번호 (숫자 10자리) */
	@Column(nullable = false, unique = true, length = 10)
	private String businessNumber;

	/** 운송주선사업 허가 번호 (BRK-001) */
	@Column(nullable = false, length = 30)
	private String licenseNumber;

	/** 대표자 */
	@Column(length = 30)
	private String representativeName;

	/** 운임 정산 주기(일). 장부의 지급 예정일 계산에 쓴다 (LEDG-020) */
	@Column(nullable = false)
	private int settlementDays;

	private Broker(String name, String businessNumber, String licenseNumber, String representativeName) {
		this.name = name;
		this.businessNumber = businessNumber;
		this.licenseNumber = licenseNumber;
		this.representativeName = representativeName;
		this.settlementDays = DEFAULT_SETTLEMENT_DAYS;
	}

	public static Broker create(String name, String businessNumber, String licenseNumber, String representativeName) {
		return new Broker(name, businessNumber, licenseNumber, representativeName);
	}

	public void changeSettlementDays(int settlementDays) {
		if (settlementDays < 0) {
			throw new IllegalArgumentException("정산 주기는 0일 이상이어야 합니다: " + settlementDays);
		}
		this.settlementDays = settlementDays;
	}
}
