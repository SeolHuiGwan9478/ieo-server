package com.ieo.server.domain.document.dto;

import java.util.List;

/** 주선사 심사 목록 한 페이지. pendingBrokers는 탭과 상관없이 심사 대기 서류가 있는 회사 수 (메뉴 배지) */
public record AdminBrokerReviewListResponse(List<AdminBrokerReviewRow> items, int page, int size, long totalCount, long pendingBrokers) {
}
