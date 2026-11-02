package com.ieo.server.domain.dispatch.dto;

/** 배차 요청. 차량을 비우면 기본 차량 */
public record CreateDispatchRequestRequest(Long vehicleId) {
}
