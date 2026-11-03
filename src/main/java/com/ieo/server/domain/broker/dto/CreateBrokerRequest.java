package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주선사 회사 등록 (BRK-001). 사업자등록번호는 하이픈이 있어도 된다. 형식과 검증 번호는 Broker가 확인한다.
 * 대표자 이름은 비워도 된다.
 */
public record CreateBrokerRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Size(max = 20) String businessNumber,
		@NotBlank @Size(max = 30) String licenseNumber,
		@Size(max = 30) String representativeName) {
}
