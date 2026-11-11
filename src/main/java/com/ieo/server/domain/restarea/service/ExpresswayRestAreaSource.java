package com.ieo.server.domain.restarea.service;

import java.util.List;

/** 한국도로공사 휴게소 목록 (편의시설·화물 휴게소). 받지 못하면 빈 목록 */
public interface ExpresswayRestAreaSource {

	List<ExpresswayRestArea> fetchAll();

	/** 도로공사 응답 한 줄 */
	record ExpresswayRestArea(
			String code,
			String name,
			String routeName,
			String direction,
			List<String> facilities,
			boolean truckRestArea,
			String phone,
			String address) {
	}
}
