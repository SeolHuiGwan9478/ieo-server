package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.Site;

/** existing: 등록 요청이 100m 안의 기존 상하차지로 이어졌으면 true (SITE-002) */
public record SiteResponse(
		Long id,
		String name,
		String address,
		double lat,
		double lng,
		int geofenceRadiusM,
		boolean existing) {

	public static SiteResponse of(Site site, boolean existing) {
		return new SiteResponse(site.getId(), site.getName(), site.getAddress(),
				site.getLat(), site.getLng(), site.getGeofenceRadiusM(), existing);
	}
}
