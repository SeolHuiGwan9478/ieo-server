package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import java.time.Instant;
import java.time.LocalDate;

/** 사업자등록증의 입력값과 국세청 진위확인 결과. 다른 서류는 null. attention이면 운영자가 눈여겨본다 */
public record BusinessCheckResponse(
		String businessNumber,
		LocalDate openedOn,
		String ownerName,
		BusinessCheckResult result,
		String resultLabel,
		BusinessStatus status,
		String statusLabel,
		String taxType,
		LocalDate closedOn,
		Instant checkedAt,
		String summary,
		boolean attention,
		boolean stub
) {

	public static BusinessCheckResponse from(BusinessCheck check) {
		if (check == null || check.getBusinessNumber() == null) {
			return null;
		}
		return new BusinessCheckResponse(
				check.formattedNumber(),
				check.getOpenedOn(),
				check.getOwnerName(),
				check.getResult(),
				check.getResult() == null ? null : check.getResult().getLabel(),
				check.getStatus(),
				check.getStatus() == null ? null : check.getStatus().getLabel(),
				check.getTaxType(),
				check.getClosedOn(),
				check.getCheckedAt(),
				check.summary(),
				check.needsAttention(),
				Boolean.TRUE.equals(check.getStub()));
	}
}
