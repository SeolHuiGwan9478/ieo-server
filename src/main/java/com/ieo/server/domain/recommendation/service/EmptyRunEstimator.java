package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.site.entity.Site;
import java.time.Duration;

/** 하차지에서 다음 상차지까지 빈 차로 가는 거리와 시간. 지금은 직선거리로 어림하고, 나중에 경로 API로 바꾼다 */
public interface EmptyRunEstimator {

	EmptyRun estimate(Site from, Site to);

	record EmptyRun(long meters, Duration duration) {
	}
}
