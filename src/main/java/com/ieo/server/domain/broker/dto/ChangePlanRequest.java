package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.enums.BrokerPlan;
import jakarta.validation.constraints.NotNull;

/** 플랜 바꾸기 (대표만). 결제가 없어 바로 적용된다 (BILL-007 테스트 모드) */
public record ChangePlanRequest(@NotNull BrokerPlan plan) {
}
