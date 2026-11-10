package com.ieo.server.domain.document.business;

import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import java.time.LocalDate;

/**
 * 국세청 진위확인 한 번의 결과. 서류(DriverDocument.businessCheck)에 기록된다.
 *
 * @param result   일치·불일치·조회 실패·조회 안 함
 * @param status   일치일 때 사업자 상태 (계속·휴업·폐업)
 * @param taxType  과세 유형 문구 (예: 부가가치세 일반과세자)
 * @param closedOn 폐업일 (폐업자일 때)
 * @param message  운영자에게 보이는 한 줄 설명. 키·주소 같은 민감한 값은 넣지 않는다
 * @param stub     시연용 가짜 응답인지. 화면에 "시연용"으로 표시한다
 */
public record BusinessLookup(
		BusinessCheckResult result,
		BusinessStatus status,
		String taxType,
		LocalDate closedOn,
		String message,
		boolean stub
) {

	public static BusinessLookup match(BusinessStatus status, String taxType, LocalDate closedOn, boolean stub) {
		return new BusinessLookup(BusinessCheckResult.MATCH, status, taxType, closedOn, null, stub);
	}

	public static BusinessLookup mismatch(String message, boolean stub) {
		return new BusinessLookup(BusinessCheckResult.MISMATCH, null, null, null, message, stub);
	}

	public static BusinessLookup error(String message) {
		return new BusinessLookup(BusinessCheckResult.ERROR, null, null, null, message, false);
	}

	public static BusinessLookup skipped(String message) {
		return new BusinessLookup(BusinessCheckResult.SKIPPED, null, null, null, message, false);
	}
}
