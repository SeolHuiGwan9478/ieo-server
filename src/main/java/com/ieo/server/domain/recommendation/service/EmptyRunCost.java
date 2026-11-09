package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 빈 차로 가는 비용 (REC-010, 011). 실수익 = 운임 − 공차 연료비 − 통행료(지금은 0) */
@Component
@RequiredArgsConstructor
public class EmptyRunCost {

	private final DieselPrices dieselPrices;
	private final RecommendationProperties properties;

	/** 공차 거리 [meters]를 이 차로 갈 때 드는 연료비(원). 경유가는 [address]가 있는 시도 평균 */
	public int fuelCost(long meters, Vehicle vehicle, String address) {
		return (int) Math.round(meters / 1000.0 / kmPerLiter(vehicle) * dieselPrices.wonPerLiter(address));
	}

	/** 직선거리를 도로 거리로 어림한다 */
	public long roadMeters(double straightMeters) {
		return Math.round(straightMeters * properties.roadFactor());
	}

	/** 실측 연비가 있으면 그것, 없으면 톤수별 기본값 (REC-011) */
	private static double kmPerLiter(Vehicle vehicle) {
		return vehicle.getMeasuredKmPerLiter() != null
				? vehicle.getMeasuredKmPerLiter().doubleValue()
				: vehicle.getTonnage().getDefaultKmPerLiter();
	}
}
