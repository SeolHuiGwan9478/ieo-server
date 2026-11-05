package com.ieo.server.domain.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 차량 앱이 보내는 위치 묶음 (TRIP-013). 끊겼던 동안 쌓인 위치를 한꺼번에 보낼 수 있다.
 * 같은 묶음을 다시 보내도 결과가 같다 (가장 늦게 잰 위치만 남긴다)
 */
public record TripLocationRequest(@NotEmpty @Size(max = 240) List<@Valid @NotNull Point> points) {

	/** 위치 하나. remainMeters·remainSeconds는 길 안내(NaviHelper) 중일 때만 */
	public record Point(
			@NotNull Instant recordedAt,
			@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
			@PositiveOrZero Integer remainMeters,
			@PositiveOrZero Integer remainSeconds) {
	}
}
