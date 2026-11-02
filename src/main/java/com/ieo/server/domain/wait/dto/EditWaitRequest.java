package com.ieo.server.domain.wait.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 시각 고치기 (WAIT-006). 세 값을 모두 보낸다. 작업 시작·이탈은 비울 수 있다 */
public record EditWaitRequest(
		@NotNull Instant arrivedAt,
		Instant workStartedAt,
		Instant departedAt) {
}
