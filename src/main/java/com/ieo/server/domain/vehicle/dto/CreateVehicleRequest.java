package com.ieo.server.domain.vehicle.dto;

import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 번호 형식과 높이 범위는 Vehicle이 확인한다. 높이는 비워도 된다. */
public record CreateVehicleRequest(
		@NotBlank @Size(max = 20) String plateNumber,
		@NotNull Tonnage tonnage,
		@NotNull BodyType bodyType,
		BigDecimal heightM) {
}
