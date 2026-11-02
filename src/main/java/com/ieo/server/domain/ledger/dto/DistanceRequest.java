package com.ieo.server.domain.ledger.dto;

import jakarta.validation.constraints.NotNull;

/** 차량 앱이 운행이 끝날 때 Pleos 주행거리계로 잰 거리(km) */
public record DistanceRequest(@NotNull Integer distanceKm) {
}
