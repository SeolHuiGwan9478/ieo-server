package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.wait.enums.WaitRounding;
import jakarta.validation.constraints.NotNull;

/** 값 범위는 WaitPolicy가 확인한다. 예: 무료 120분, 30분당 10,000원, 상한 50,000원, 올림 */
public record WaitPolicyRequest(
		@NotNull Integer freeMinutes,
		@NotNull Integer unitMinutes,
		@NotNull Integer unitAmount,
		@NotNull Integer maxAmount,
		@NotNull WaitRounding rounding) {
}
