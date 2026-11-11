package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.recommendation.service.TruckSpec;
import java.util.List;
import java.util.Optional;

/** 화물차 경로의 길 모양과 경로 위 졸음쉼터. 받지 못하면 비어 있다 */
public interface TruckRouteLineSource {

	Optional<RouteLine> route(double fromLat, double fromLng, double toLat, double toLng, TruckSpec spec);

	/**
	 * @param points   경로를 따라 이은 좌표 [위도, 경도]
	 * @param shelters 경로 위 졸음쉼터 (TMAP turnType 150)
	 */
	record RouteLine(List<double[]> points, List<Shelter> shelters) {
	}

	record Shelter(String name, double latitude, double longitude) {
	}
}
