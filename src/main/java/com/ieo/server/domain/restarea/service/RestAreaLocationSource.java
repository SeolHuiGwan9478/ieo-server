package com.ieo.server.domain.restarea.service;

import java.util.List;

/** 전국휴게소정보표준데이터의 휴게소 좌표·상하행. 받지 못하면 빈 목록 */
public interface RestAreaLocationSource {

	List<RestAreaLocation> fetchAll();

	/** 표준데이터 한 줄. name은 "옥산(부산)"처럼 "휴게소"가 빠져 있다 */
	record RestAreaLocation(String name, String roadDirection, double latitude, double longitude) {
	}
}
