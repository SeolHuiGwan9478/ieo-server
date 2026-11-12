package com.ieo.server.domain.broker.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.regex.Pattern;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 주선사(운송주선업체) 회사. 담당자 계정은 broker_members로 연결한다. (요구사항: docs/requirements/07_주선사.md)
 */
@Getter
@Entity
@Table(name = "brokers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Broker extends BaseTimeEntity {

	private static final int DEFAULT_SETTLEMENT_DAYS = 30;
	private static final int MAX_SETTLEMENT_DAYS = 365;
	private static final BigDecimal MAX_FEE_RATE = new BigDecimal("99.9");
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

	/**
	 * 기본 수수료율(%). 오더를 올릴 때 지급운임에서 청구운임을 미리 계산하는 데 쓴다.
	 * 수수료율 = 수수료 ÷ 청구운임 (화주 계약 금액 기준, 주선수수료 상한 논의와 같은 기준). 0이면 자동 계산 안 함
	 */
	@Column(nullable = false, precision = 4, scale = 1)
	@ColumnDefault("0")
	private BigDecimal defaultFeeRate;

	private Broker(String name, String businessNumber, String licenseNumber, String representativeName,
			int settlementDays, BigDecimal defaultFeeRate) {
		this.name = name;
		this.businessNumber = businessNumber;
		this.licenseNumber = licenseNumber;
		this.representativeName = representativeName;
		this.settlementDays = settlementDays;
		this.defaultFeeRate = defaultFeeRate;
	}

	/** 사업자등록번호는 하이픈을 지우고 검증 번호까지 확인한다. 허가 번호는 필수(BRK-001). 정산 주기는 30일 */
	public static Broker create(String name, String businessNumber, String licenseNumber, String representativeName) {
		return create(name, businessNumber, licenseNumber, representativeName, null);
	}

	/** 정산 주기를 비우면 30일 */
	public static Broker create(String name, String businessNumber, String licenseNumber, String representativeName,
			Integer settlementDays) {
		return create(name, businessNumber, licenseNumber, representativeName, settlementDays, null);
	}

	/** 정산 주기를 비우면 30일, 기본 수수료율을 비우면 0% */
	public static Broker create(String name, String businessNumber, String licenseNumber, String representativeName,
			Integer settlementDays, BigDecimal defaultFeeRate) {
		return new Broker(name, normalizeBusinessNumber(businessNumber), requireLicenseNumber(licenseNumber),
				representativeName,
				settlementDays == null ? DEFAULT_SETTLEMENT_DAYS : validateSettlementDays(settlementDays),
				defaultFeeRate == null ? BigDecimal.ZERO.setScale(1) : validateFeeRate(defaultFeeRate));
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

	/** 상호. 비울 수 없다 */
	public void rename(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "상호를 입력해 주세요.");
		}
		this.name = trimmed;
	}

	/** 허가 번호. 비울 수 없다 (BRK-001) */
	public void changeLicenseNumber(String licenseNumber) {
		this.licenseNumber = requireLicenseNumber(licenseNumber);
	}

	/** 대표자 이름. 빈 값이면 지운다 */
	public void changeRepresentativeName(String representativeName) {
		String trimmed = representativeName == null ? "" : representativeName.strip();
		this.representativeName = trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * 정산 주기 (BRK-003). 0~365일 (오더 지급일 범위와 같다).
	 * 이미 지급 예정일이 계산된 장부는 바꾸지 않는다. 아직 인수증을 보내기 전인 운행은 보낼 때의 값을 쓴다.
	 */
	public void changeSettlementDays(int settlementDays) {
		this.settlementDays = validateSettlementDays(settlementDays);
	}

	/** 기본 수수료율 (0~99.9%). 100%면 청구운임을 계산할 수 없다 */
	public void changeDefaultFeeRate(BigDecimal feeRate) {
		this.defaultFeeRate = validateFeeRate(feeRate);
	}

	/** 소수 첫째 자리까지. 0~99.9% 밖이면 400 BROKER_400_FEE_RATE */
	public static BigDecimal validateFeeRate(BigDecimal feeRate) {
		if (feeRate == null || feeRate.signum() < 0 || feeRate.compareTo(MAX_FEE_RATE) > 0) {
			throw new BusinessException(ErrorCode.BROKER_INVALID_FEE_RATE);
		}
		return feeRate.setScale(1, RoundingMode.HALF_UP);
	}

	private static int validateSettlementDays(int settlementDays) {
		if (settlementDays < 0 || settlementDays > MAX_SETTLEMENT_DAYS) {
			throw new BusinessException(ErrorCode.BROKER_INVALID_SETTLEMENT_DAYS);
		}
		return settlementDays;
	}
}
