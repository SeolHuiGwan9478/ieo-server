package com.ieo.server.domain.vehicle.dto;

import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 톤수·차종·높이를 한 번에 바꾼다. 높이를 null로 보내면 비운다. 번호는 바꿀 수 없다. */
public record UpdateVehicleRequest(@NotNull Tonnage tonnage, @NotNull BodyType bodyType, BigDecimal heightM,
		Integer widthCm, Integer lengthCm, Integer totalWeightKg) {
}
