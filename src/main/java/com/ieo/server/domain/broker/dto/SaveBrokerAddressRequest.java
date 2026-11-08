package com.ieo.server.domain.broker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveBrokerAddressRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 100) String detailAddress,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
}
