package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.Site;
import java.util.List;

/** 정차 중 화면의 정보 카드 (SITE-007). heightWarning은 차주가 아니면 UNKNOWN. stats는 대기 기록이 3건 미만이면 null */
public record SiteCardResponse(
		Long id,
		String name,
		String address,
		double lat,
		double lng,
		int geofenceRadiusM,
		SiteAttributesResponse attributes,
		HeightWarning heightWarning,
		SiteStatsResponse stats,
		List<SiteNoteResponse> notes) {

	public static SiteCardResponse of(Site site, SiteAttributesResponse attributes, HeightWarning heightWarning,
			SiteStatsResponse stats, List<SiteNoteResponse> notes) {
		return new SiteCardResponse(site.getId(), site.getName(), site.getAddress(), site.getLat(), site.getLng(),
				site.getGeofenceRadiusM(), attributes, heightWarning, stats, notes);
	}
}
