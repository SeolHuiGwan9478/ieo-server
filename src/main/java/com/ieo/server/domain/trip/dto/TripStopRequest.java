package com.ieo.server.domain.trip.dto;

import com.ieo.server.domain.site.dto.CreateSiteRequest;
import jakarta.validation.Valid;
import java.time.Instant;

/**
 * 상하차지 하나. 이미 있는 상하차지면 siteId를, 주소 검색으로 고른 곳이면 site를 보낸다 (TRIP-002).
 * site는 100m 안에 같은 곳이 있으면 그것을 쓴다. 둘 다 보내면 siteId를 쓴다
 */
public record TripStopRequest(
		Long siteId,
		@Valid CreateSiteRequest site,
		Instant plannedAt) {
}
