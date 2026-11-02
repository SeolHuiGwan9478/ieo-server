package com.ieo.server.domain.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 좌표·반경 범위는 Site가 확인한다. 반경을 비우면 200m */
public record CreateSiteRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 200) String address,
		@NotNull Double lat,
		@NotNull Double lng,
		Integer geofenceRadiusM) {
}
