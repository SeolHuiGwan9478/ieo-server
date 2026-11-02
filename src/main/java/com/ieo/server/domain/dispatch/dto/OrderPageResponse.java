package com.ieo.server.domain.dispatch.dto;

import java.util.List;

/** 페이지 목록. page는 0부터 */
public record OrderPageResponse(List<OrderResponse> items, long totalCount, int page, int size) {
}
