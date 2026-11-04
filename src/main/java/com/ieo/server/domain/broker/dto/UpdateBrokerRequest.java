package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.Size;

/**
 * 회사 정보 수정 (대표만). 보낸 항목만 바꾸고, 비운(null) 항목은 그대로 둔다.
 * 상호·허가 번호는 빈 값으로 바꿀 수 없고, 대표자 이름은 빈 문자열을 보내면 지운다.
 * 사업자등록번호는 회사를 식별하는 값이라 바꿀 수 없다.
 */
public record UpdateBrokerRequest(
		@Size(max = 50) String name,
		@Size(max = 30) String licenseNumber,
		@Size(max = 30) String representativeName,
		Integer settlementDays) {
}
