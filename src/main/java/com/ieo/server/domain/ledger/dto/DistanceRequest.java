package com.ieo.server.domain.ledger.dto;

import jakarta.validation.constraints.NotNull;

/** 차량 앱이 운행을 마칠 때 잰 거리(km): 상차지를 떠날 때부터 주행거리계 차이, 못 읽으면 내비 경로 거리 */
public record DistanceRequest(@NotNull Integer distanceKm) {
}
