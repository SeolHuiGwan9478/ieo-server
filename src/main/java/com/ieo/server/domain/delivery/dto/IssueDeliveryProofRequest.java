package com.ieo.server.domain.delivery.dto;

/** 차량 앱이 "인수증 받기"를 누를 때의 차량 위치 (선택) */
public record IssueDeliveryProofRequest(Double lat, Double lng) {
}
