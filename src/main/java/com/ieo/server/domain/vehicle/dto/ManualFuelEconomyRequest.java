package com.ieo.server.domain.vehicle.dto;

import java.math.BigDecimal;

/** 차주가 넣는 평소 연비(km/L). null이면 비우고 톤수 기본값을 쓴다 */
public record ManualFuelEconomyRequest(BigDecimal kmPerLiter) {
}
