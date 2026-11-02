package com.ieo.server.domain.vehicle.dto;

import jakarta.validation.constraints.NotNull;

/** Pleos getCumulativeFuelEconomy() 값을 그대로 보낸다. 단위 L/100km */
public record FuelEconomyRequest(@NotNull Float litersPer100Km) {
}
