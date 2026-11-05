package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.site.entity.Site;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 직선거리 × 도로 계수, 시간은 평균 속도로 (docs/design/04_배차추천.md) */
@Component
@RequiredArgsConstructor
public class StraightLineEmptyRunEstimator implements EmptyRunEstimator {

	private final RecommendationProperties properties;

	@Override
	public EmptyRun estimate(Site from, Site to) {
		long meters = Math.round(from.distanceMeters(to.getLat(), to.getLng()) * properties.roadFactor());
		long seconds = Math.round(meters / (properties.averageSpeedKph() * 1000 / 3600));
		return new EmptyRun(meters, Duration.ofSeconds(seconds));
	}
}
