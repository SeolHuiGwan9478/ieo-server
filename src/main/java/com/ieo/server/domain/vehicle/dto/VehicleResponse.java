package com.ieo.server.domain.vehicle.dto;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;
import java.time.Instant;

/** 앱이 바로 보여 줄 수 있게 톤수·차종의 화면 표기(label)를 같이 준다. VIN은 주지 않고 연결 여부만 준다. */
public record VehicleResponse(
		Long id,
		String plateNumber,
		Tonnage tonnage,
		String tonnageLabel,
		BodyType bodyType,
		String bodyTypeLabel,
		BigDecimal heightM,
		BigDecimal measuredKmPerLiter,
		boolean pleosLinked,
		boolean defaultVehicle,
		Instant createdAt) {

	public static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(
				vehicle.getId(),
				vehicle.getPlateNumber(),
				vehicle.getTonnage(),
				vehicle.getTonnage().getLabel(),
				vehicle.getBodyType(),
				vehicle.getBodyType().getLabel(),
				vehicle.getHeightM(),
				vehicle.getMeasuredKmPerLiter(),
				vehicle.getVinHash() != null,
				vehicle.isDefaultVehicle(),
				vehicle.getCreatedAt());
	}
}
