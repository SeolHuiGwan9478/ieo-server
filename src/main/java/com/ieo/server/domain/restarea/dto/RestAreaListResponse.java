package com.ieo.server.domain.restarea.dto;

import java.util.List;

/** @param attribution 화면에 휴게소를 보여 줄 때 함께 적을 출처 (표준데이터 이용허락범위: 저작자표시) */
public record RestAreaListResponse(List<RestAreaResponse> items, String attribution) {

	public static final String ATTRIBUTION = "휴게소 정보: 공공데이터포털 전국휴게소정보표준데이터, 한국도로공사 고속도로 공공데이터 포털";
}
