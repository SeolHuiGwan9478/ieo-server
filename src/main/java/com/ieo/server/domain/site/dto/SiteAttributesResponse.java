package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.SiteAttribute;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 항목별 최신 값. 아무도 남기지 않은 항목은 null. updatedAt은 그중 가장 최근에 남긴 시각 */
public record SiteAttributesResponse(BigDecimal heightLimitM, Boolean forklift, Boolean parking, Instant updatedAt) {

	public static SiteAttributesResponse from(List<SiteAttribute> latest) {
		BigDecimal heightLimitM = null;
		Boolean forklift = null;
		Boolean parking = null;
		for (SiteAttribute attribute : latest) {
			switch (attribute.getKey()) {
				case HEIGHT_LIMIT -> heightLimitM = attribute.heightLimitM();
				case FORKLIFT -> forklift = attribute.flag();
				case PARKING -> parking = attribute.flag();
			}
		}
		Instant updatedAt = latest.stream()
				.map(SiteAttribute::getCreatedAt)
				.max(Comparator.naturalOrder())
				.orElse(null);
		return new SiteAttributesResponse(heightLimitM, forklift, parking, updatedAt);
	}
}
