package com.ieo.server.domain.document.dto;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 사업자등록증을 낼 때 함께 보내는 값 (multipart 필드). 다른 서류는 비워 보낸다.
 *
 * @param businessNumber 사업자등록번호 (123-45-67890 또는 1234567890)
 * @param openedOn       개업일 (2020-01-31 또는 20200131)
 * @param ownerName      대표자 이름. 비우면 가입자 이름 (지입 차주처럼 명의가 다를 때만 적는다)
 */
public record BusinessInput(String businessNumber, String openedOn, String ownerName) {

	private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

	public boolean isEmpty() {
		return isBlank(businessNumber) && isBlank(openedOn) && isBlank(ownerName);
	}

	public LocalDate openedOnDate() {
		if (isBlank(openedOn)) {
			return null;
		}
		String value = openedOn.strip();
		try {
			return value.length() == 8 ? LocalDate.parse(value, COMPACT) : LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "개업일은 2020-01-31 형식으로 적어 주세요.");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
