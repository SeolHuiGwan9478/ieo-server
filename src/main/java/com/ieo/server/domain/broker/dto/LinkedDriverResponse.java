package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.driver.dto.DriverRecordResponse;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.time.Instant;

/**
 * 우리 회사 단골 차주. 지정 배차할 때 driverUserId를 오더의 targetDriverId로 쓴다.
 * verified(서류 인증 마크)와 이행 기록(완료 운행 수, 늦은 취소 수)을 같이 준다 (DSP-031).
 * 기본 차량이 없으면 차량 항목은 null
 */
public record LinkedDriverResponse(
		Long linkId,
		Long driverUserId,
		String name,
		String phone,
		String plateNumber,
		Tonnage tonnage,
		String tonnageLabel,
		BodyType bodyType,
		String bodyTypeLabel,
		Instant linkedAt,
		boolean verified,
		long completedTripCount,
		long lateCancelCount) {

	public static LinkedDriverResponse from(BrokerDriverLink link, Vehicle vehicle, DriverRecordResponse record) {
		return new LinkedDriverResponse(
				link.getId(),
				link.getDriver().getUserId(),
				link.getDriver().getUser().getName(),
				link.getDriver().getUser().getPhone(),
				vehicle == null ? null : vehicle.getPlateNumber(),
				vehicle == null ? null : vehicle.getTonnage(),
				vehicle == null ? null : vehicle.getTonnage().getLabel(),
				vehicle == null ? null : vehicle.getBodyType(),
				vehicle == null ? null : vehicle.getBodyType().getLabel(),
				link.getLinkedAt(),
				record.verified(),
				record.completedTripCount(),
				record.lateCancelCount());
	}
}
