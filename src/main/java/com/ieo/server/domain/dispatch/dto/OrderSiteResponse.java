package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.site.entity.Site;

public record OrderSiteResponse(Long id, String name, String address, double lat, double lng) {

	public static OrderSiteResponse from(Site site) {
		return new OrderSiteResponse(site.getId(), site.getName(), site.getAddress(), site.getLat(), site.getLng());
	}

	/** 시·구까지만 ("경기 평택시 포승읍 평택항로 156" → "경기 평택시"). 좌표는 소수 둘째 자리(1km 남짓)로 뭉갠다. id·이름은 비운다 */
	public OrderSiteResponse coarse() {
		String[] words = address == null ? new String[0] : address.trim().split("\\s+");
		String region = String.join(" ", java.util.Arrays.copyOf(words, Math.min(2, words.length)));
		return new OrderSiteResponse(null, region, region, Math.round(lat * 100) / 100.0, Math.round(lng * 100) / 100.0);
	}
}
