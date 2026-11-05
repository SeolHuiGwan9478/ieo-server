package com.ieo.server.domain.broker.dto;

import java.util.List;

/** 주선사 운행 목록 페이지. page는 0부터 */
public record BrokerTripPageResponse(List<BrokerTripResponse> items, long totalCount, int page, int size) {
}
