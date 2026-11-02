package com.ieo.server.domain.trip.dto;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import java.time.Instant;

/** 차량 앱이 지오펜스를 걸 수 있게 상하차지 좌표와 반경을 같이 준다 */
public record TripStopResponse(
		Long id,
		StopType type,
		Long siteId,
		String name,
		String address,
		double lat,
		double lng,
		int geofenceRadiusM,
		Instant plannedAt,
		Instant arrivedAt,
		Instant departedAt) {

	public static TripStopResponse from(TripStop stop) {
		Site site = stop.getSite();
		return new TripStopResponse(stop.getId(), stop.getType(), site.getId(), site.getName(), site.getAddress(),
				site.getLat(), site.getLng(), site.getGeofenceRadiusM(),
				stop.getPlannedAt(), stop.getArrivedAt(), stop.getDepartedAt());
	}
}
