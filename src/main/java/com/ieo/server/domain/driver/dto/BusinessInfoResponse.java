package com.ieo.server.domain.driver.dto;

import com.ieo.server.domain.driver.entity.Driver;

/** 등록하지 않았으면 셋 다 null이다 */
public record BusinessInfoResponse(String businessName, String businessNumber, String representativeName) {

	public static BusinessInfoResponse from(Driver driver) {
		return new BusinessInfoResponse(driver.getBusinessName(), driver.getBusinessNumber(), driver.getRepresentativeName());
	}
}
