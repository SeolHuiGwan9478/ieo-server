package com.ieo.server.domain.vehicle.dto;

import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 차량 정보를 한 번에 바꾼다. 높이를 null로 보내면 비운다. plateNumber를 비우면 번호는 그대로.
 * 번호·톤수·차종이 바뀌면 자동차등록증을 다시 받는다 (VEH-001)
 */
public record UpdateVehicleRequest(@NotNull Tonnage tonnage, @NotNull BodyType bodyType, BigDecimal heightM,
		Integer widthCm, Integer lengthCm, Integer totalWeightKg, Integer axleCount, String plateNumber) {
}
