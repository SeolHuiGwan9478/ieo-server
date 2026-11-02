package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.Site;

/** 근처 상하차지 한 곳. distanceM은 요청 좌표에서의 직선 거리(m, 반올림) */
public record NearbySiteResponse(
		Long id,
		String name,
		String address,
		double lat,
		double lng,
		int geofenceRadiusM,
		long distanceM) {

	public static NearbySiteResponse of(Site site, double distanceM) {
		return new NearbySiteResponse(site.getId(), site.getName(), site.getAddress(),
				site.getLat(), site.getLng(), site.getGeofenceRadiusM(), Math.round(distanceM));
	}
}
