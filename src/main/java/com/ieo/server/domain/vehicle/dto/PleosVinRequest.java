package com.ieo.server.domain.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 차량 앱이 Pleos getVINInfo()로 읽은 값 */
public record PleosVinRequest(@NotBlank @Size(max = 32) String vin) {
}
