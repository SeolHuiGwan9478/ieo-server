package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.util.Collection;
import java.util.Map;

/** 트럭이 실제로 달릴 경로의 거리·시간·통행료. 받지 못한 구간은 결과에 없고, 호출한 쪽이 어림값을 쓴다 */
public interface TruckRouteEstimator {

	Map<Leg, TruckRoute> routes(Collection<Leg> legs, Vehicle vehicle);

	record Leg(double fromLat, double fromLng, double toLat, double toLng) {
	}

	/** @param toll 예상 통행료(원) */
	record TruckRoute(long meters, long seconds, int toll) {
	}
}
