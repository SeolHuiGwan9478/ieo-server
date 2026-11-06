package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 주선사 회사 등록 (BRK-001). 사업자등록번호는 하이픈이 있어도 된다. 형식과 검증 번호는 Broker가 확인한다.
 * 대표자 이름은 비워도 된다. 정산 주기(BRK-003)를 비우면 30일이고, 범위(0~365일)는 Broker가 확인한다.
 * 기본 수수료율(%)을 비우면 0 (오더 청구운임 자동 계산 안 함)
 */
public record CreateBrokerRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Size(max = 20) String businessNumber,
		@NotBlank @Size(max = 30) String licenseNumber,
		@Size(max = 30) String representativeName,
		Integer settlementDays,
		BigDecimal defaultFeeRate) {

	/** 정산 주기를 비운 등록 (30일) */
	public CreateBrokerRequest(String name, String businessNumber, String licenseNumber, String representativeName) {
		this(name, businessNumber, licenseNumber, representativeName, null, null);
	}

	/** 기본 수수료율을 비운 등록 (0%) */
	public CreateBrokerRequest(String name, String businessNumber, String licenseNumber, String representativeName,
			Integer settlementDays) {
		this(name, businessNumber, licenseNumber, representativeName, settlementDays, null);
	}
}
