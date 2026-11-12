package com.ieo.server.domain.plan.dto;

import com.ieo.server.domain.plan.enums.DriverPlan;
import jakarta.validation.constraints.NotNull;

/** 플랜 고르기. 유료면 데모 결제 후 바로 적용, BASIC이면 해지 */
public record ChooseDriverPlanRequest(@NotNull DriverPlan plan) {
}
