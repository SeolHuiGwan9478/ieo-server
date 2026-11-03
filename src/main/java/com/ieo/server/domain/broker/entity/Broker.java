package com.ieo.server.domain.broker.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.regex.Pattern;
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
	private static final Pattern BUSINESS_NUMBER = Pattern.compile("\\d{10}");
	private static final int[] CHECK_WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

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

	/** 사업자등록번호는 하이픈을 지우고 검증 번호까지 확인한다. 허가 번호는 필수(BRK-001) */
	public static Broker create(String name, String businessNumber, String licenseNumber, String representativeName) {
		return new Broker(name, normalizeBusinessNumber(businessNumber), requireLicenseNumber(licenseNumber),
				representativeName);
	}

	/**
	 * 띄어쓰기·하이픈을 지우고 숫자 10자리와 마지막 검증 번호를 확인한다 (국세청 계산식).
	 * 실제로 있는 사업자인지는 확인하지 않는다.
	 */
	public static String normalizeBusinessNumber(String businessNumber) {
		String digits = businessNumber == null ? "" : businessNumber.replaceAll("[\\s-]", "");
		if (!BUSINESS_NUMBER.matcher(digits).matches() || !hasValidCheckDigit(digits)) {
			throw new BusinessException(ErrorCode.BROKER_INVALID_BUSINESS_NUMBER);
		}
		return digits;
	}

	private static boolean hasValidCheckDigit(String digits) {
		int sum = 0;
		for (int i = 0; i < CHECK_WEIGHTS.length; i++) {
			sum += (digits.charAt(i) - '0') * CHECK_WEIGHTS[i];
		}
		sum += (digits.charAt(8) - '0') * 5 / 10;
		return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
	}

	private static String requireLicenseNumber(String licenseNumber) {
		String trimmed = licenseNumber == null ? "" : licenseNumber.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.BROKER_INVALID_LICENSE_NUMBER);
		}
		return trimmed;
	}

	public void changeSettlementDays(int settlementDays) {
		if (settlementDays < 0) {
			throw new IllegalArgumentException("정산 주기는 0일 이상이어야 합니다: " + settlementDays);
		}
		this.settlementDays = settlementDays;
	}
}
