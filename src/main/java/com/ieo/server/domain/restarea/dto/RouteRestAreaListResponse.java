package com.ieo.server.domain.restarea.dto;

import java.util.List;

/**
 * @param routed      경로를 받았다. false면 TMAP 키가 없거나 실패해서 items가 비어 있다
 * @param items       경로 거리순
 * @param attribution 화면에 함께 적을 출처
 */
public record RouteRestAreaListResponse(boolean routed, List<RouteRestAreaResponse> items, String attribution) {
}
