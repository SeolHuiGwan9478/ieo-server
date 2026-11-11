package com.ieo.server.domain.restarea.dto;

import com.ieo.server.domain.restarea.service.RouteRestAreaPicker.Kind;
import com.ieo.server.domain.restarea.service.RouteRestAreaPicker.Pick;
import java.util.List;

/**
 * 경로 위 쉴 곳 하나.
 *
 * @param kind        LOUNGE(수면실·샤워실·세탁실이 있는 휴게소) 또는 DROWSY_SHELTER(졸음쉼터)
 * @param id          휴게소 id. 졸음쉼터는 없음
 * @param name        "성주휴게소", "아포졸음쉼터"
 * @param direction   가는 쪽 도시 (휴게소만)
 * @param aheadMeters 지금 위치에서 경로를 따라간 거리
 */
public record RouteRestAreaResponse(
		Kind kind,
		Long id,
		String name,
		String direction,
		int aheadMeters,
		List<String> facilities,
		String phone,
		double latitude,
		double longitude) {

	public static RouteRestAreaResponse of(Pick pick) {
		if (pick.kind() == Kind.DROWSY_SHELTER) {
			return new RouteRestAreaResponse(pick.kind(), null, pick.shelter().name(), null, pick.aheadMeters(), List.of(), null,
					pick.shelter().latitude(), pick.shelter().longitude());
		}
		var area = pick.area();
		return new RouteRestAreaResponse(pick.kind(), area.getId(), RestAreaResponse.displayName(area.getName()),
				area.getDirection(), pick.aheadMeters(), area.facilityList(), area.getPhone(), area.latitude(), area.longitude());
	}
}
