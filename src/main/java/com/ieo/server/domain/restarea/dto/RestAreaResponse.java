package com.ieo.server.domain.restarea.dto;

import com.ieo.server.domain.restarea.entity.RestArea;
import java.util.List;

/**
 * 휴게소 한 곳.
 *
 * @param name           "옥산휴게소"처럼 방향을 뺀 이름
 * @param direction      가는 쪽 도시 (예: 부산). 도로공사 값
 * @param roadDirection  상행·하행
 * @param distanceMeters 지금 자리에서 직선거리
 * @param lounge         수면실·샤워실·세탁실이 모두 있다 (화물차 라운지가 있는 휴게소)
 */
public record RestAreaResponse(
		Long id,
		String name,
		String direction,
		String routeName,
		String roadDirection,
		int distanceMeters,
		List<String> facilities,
		boolean lounge,
		boolean truckRestArea,
		String phone,
		double latitude,
		double longitude) {

	public static RestAreaResponse of(RestArea area, int distanceMeters) {
		return new RestAreaResponse(area.getId(), displayName(area.getName()), area.getDirection(), area.getRouteName(),
				area.getRoadDirection(), distanceMeters, area.facilityList(), area.hasTruckLounge(), area.isTruckRestArea(),
				area.getPhone(), area.latitude(), area.longitude());
	}

	/** "옥산(부산)휴게소" → "옥산휴게소", "처인휴게소"는 그대로 */
	public static String displayName(String name) {
		int open = name.indexOf('(');
		if (open < 0) {
			return name;
		}
		String base = name.substring(0, open);
		return base.endsWith("휴게소") ? base : base + "휴게소";
	}
}
