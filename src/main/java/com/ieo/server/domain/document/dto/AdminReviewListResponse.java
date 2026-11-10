package com.ieo.server.domain.document.dto;

import java.util.List;

/** 심사 목록 한 페이지. pendingDrivers는 탭과 상관없이 심사 대기 서류가 있는 차주 수 (메뉴 배지용) */
public record AdminReviewListResponse(List<AdminReviewRow> items, int page, int size, long totalCount, long pendingDrivers) {
}
