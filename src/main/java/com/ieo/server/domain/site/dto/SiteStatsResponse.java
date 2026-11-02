package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.SiteStats;
import java.time.Instant;

/** 정보 카드의 평균 대기. 기록이 3건 미만이면 카드에 넣지 않는다 */
public record SiteStatsResponse(Integer avgWaitMinutes, Integer avgWorkMinutes, int sampleCount, Instant calculatedAt) {

	public static SiteStatsResponse from(SiteStats stats) {
		return new SiteStatsResponse(stats.getAvgWaitMinutes(), stats.getAvgWorkMinutes(), stats.getSampleCount(),
				stats.getCalculatedAt());
	}
}
