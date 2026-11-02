package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.site.entity.Site;

public record OrderSiteResponse(Long id, String name, String address, double lat, double lng) {

	public static OrderSiteResponse from(Site site) {
		return new OrderSiteResponse(site.getId(), site.getName(), site.getAddress(), site.getLat(), site.getLng());
	}
}
