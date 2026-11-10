package com.ieo.server.domain.vehicle.dto;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.FuelEconomySource;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 앱이 바로 보여 줄 수 있게 톤수·차종의 화면 표기(label)를 같이 준다. VIN은 주지 않고 연결 여부만 준다.
 * kmPerLiter는 기름값 계산에 실제로 쓰는 연비이고, fuelEconomySource가 그 출처다 (Pleos → 직접 입력 → 톤수 기본값).
 */
public record VehicleResponse(
		Long id,
		String plateNumber,
		Tonnage tonnage,
		String tonnageLabel,
		BodyType bodyType,
		String bodyTypeLabel,
		BigDecimal heightM,
		Integer widthCm,
		Integer lengthCm,
		Integer totalWeightKg,
		Integer axleCount,
		BigDecimal measuredKmPerLiter,
		BigDecimal manualKmPerLiter,
		double kmPerLiter,
		FuelEconomySource fuelEconomySource,
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
				vehicle.getWidthCm(),
				vehicle.getLengthCm(),
				vehicle.getTotalWeightKg(),
				vehicle.getAxleCount(),
				vehicle.getMeasuredKmPerLiter(),
				vehicle.getManualKmPerLiter(),
				vehicle.kmPerLiter(),
				vehicle.fuelEconomySource(),
				vehicle.getVinHash() != null,
				vehicle.isDefaultVehicle(),
				vehicle.getCreatedAt());
	}
}
