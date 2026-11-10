package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.document.business.BusinessLookup;
import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사업자등록증 서류에 붙는 입력값과 국세청 진위확인 결과 (07 인계서 12장). 다른 서류는 비어 있다.
 * 입력값(번호·개업일·대표자)은 차주가 적은 것이라, 운영자는 사진의 값과 같은지 먼저 보고 조회 결과를 참고한다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessCheck {

	public static final int MAX_OWNER_NAME = 50;
	public static final int MAX_MESSAGE = 200;
	private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};
	private static final LocalDate EARLIEST_OPENING = LocalDate.of(1900, 1, 1);

	/** 사업자등록번호. 숫자 10자리 */
	@Column(name = "business_number", length = 10)
	private String businessNumber;

	@Column(name = "business_opened_on")
	private LocalDate openedOn;

	/** 국세청에 맞춰 본 대표자 이름. 차주가 따로 적지 않으면 가입자 이름 */
	@Column(name = "business_owner_name", length = MAX_OWNER_NAME)
	private String ownerName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "business_check_result", length = 20)
	private BusinessCheckResult result;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "business_status", length = 20)
	private BusinessStatus status;

	@Column(name = "business_tax_type", length = 60)
	private String taxType;

	@Column(name = "business_closed_on")
	private LocalDate closedOn;

	@Column(name = "business_checked_at")
	private Instant checkedAt;

	/** 조회 결과 한 줄 설명 (불일치 사유, 실패 이유, 건너뛴 이유) */
	@Column(name = "business_check_message", length = MAX_MESSAGE)
	private String message;

	/** 시연용 스텁 응답이면 true. 화면에 "시연용"으로 보인다. 다른 서류는 전부 null이라 Boolean */
	@Column(name = "business_check_stub")
	private Boolean stub;

	private BusinessCheck(String businessNumber, LocalDate openedOn, String ownerName) {
		this.businessNumber = businessNumber;
		this.openedOn = openedOn;
		this.ownerName = ownerName;
	}

	/**
	 * 차주 입력값을 검사해 만든다. 번호는 하이픈을 빼고 10자리와 검증 숫자를, 개업일은 1900년 이후 오늘까지를 받는다.
	 *
	 * @param rawNumber 예: 123-45-67890 또는 1234567890
	 */
	public static BusinessCheck of(String rawNumber, LocalDate openedOn, String ownerName) {
		String number = rawNumber == null ? "" : rawNumber.replaceAll("[\\s-]", "");
		if (!isValidNumber(number)) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "사업자등록번호 10자리를 확인해 주세요.");
		}
		if (openedOn == null) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "개업일을 입력해 주세요.");
		}
		if (openedOn.isBefore(EARLIEST_OPENING) || openedOn.isAfter(LocalDate.now().plusDays(1))) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "개업일을 확인해 주세요.");
		}
		String name = ownerName == null ? "" : ownerName.strip();
		if (name.isEmpty() || name.length() > MAX_OWNER_NAME) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "대표자 이름을 입력해 주세요.");
		}
		return new BusinessCheck(number, openedOn, name);
	}

	/** 사업자등록번호 검증 숫자 (마지막 자리). 가중치 1,3,7,1,3,7,1,3,5 */
	public static boolean isValidNumber(String digits) {
		if (digits == null || digits.length() != 10 || !digits.chars().allMatch(Character::isDigit)) {
			return false;
		}
		int sum = 0;
		for (int i = 0; i < 9; i++) {
			sum += (digits.charAt(i) - '0') * WEIGHTS[i];
		}
		sum += (digits.charAt(8) - '0') * 5 / 10;
		return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
	}

	public void record(BusinessLookup lookup, Instant now) {
		this.result = lookup.result();
		this.status = lookup.status();
		this.taxType = lookup.taxType();
		this.closedOn = lookup.closedOn();
		this.message = lookup.message() == null || lookup.message().length() <= MAX_MESSAGE ? lookup.message() : lookup.message().substring(0, MAX_MESSAGE);
		this.stub = lookup.stub();
		this.checkedAt = now;
	}

	public String formattedNumber() {
		return businessNumber.substring(0, 3) + "-" + businessNumber.substring(3, 5) + "-" + businessNumber.substring(5);
	}

	/** 운영자 화면과 이력에 쓰는 한 줄 요약 */
	public String summary() {
		if (result == null) {
			return "아직 조회하지 않았어요";
		}
		String text = switch (result) {
			case MATCH -> "국세청 등록 정보와 일치" + (status == null ? "" : " · " + status.getLabel()) + (taxType == null ? "" : " · " + taxType)
					+ (closedOn == null ? "" : " · 폐업 " + closedOn);
			case MISMATCH -> "국세청 등록 정보와 달라요" + (message == null ? "" : " (" + message + ")");
			case ERROR -> "조회하지 못했어요" + (message == null ? "" : " (" + message + ")");
			case SKIPPED -> message == null ? "조회하지 않았어요" : message;
		};
		return Boolean.TRUE.equals(stub) ? text + " [시연용 응답]" : text;
	}

	/** 운영자가 눈여겨봐야 하는 결과: 불일치, 휴업·폐업, 조회 실패 */
	public boolean needsAttention() {
		if (result == null) {
			return false;
		}
		return switch (result) {
			case MATCH -> status != BusinessStatus.ACTIVE;
			case MISMATCH, ERROR -> true;
			case SKIPPED -> false;
		};
	}
}
