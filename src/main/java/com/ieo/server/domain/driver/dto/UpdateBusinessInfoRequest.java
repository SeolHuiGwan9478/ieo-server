package com.ieo.server.domain.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBusinessInfoRequest(
		@NotBlank @Size(max = 50) String businessName,
		@NotBlank @Size(max = 20) String businessNumber,
		@NotBlank @Size(max = 30) String representativeName) {
}
