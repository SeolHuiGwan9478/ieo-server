package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;

/**
 * TMAP에 넘기는 차량 정보. 앱은 톤수와 높이만 알아서 나머지는 톤수별 기본값을 쓴다.
 * carType은 고속도로 통행료 차종 번호다(1~5종). 톤수와 차종의 대응은 가안이다 (docs/design/04_배차추천.md)
 */
record TruckSpec(int carType, int widthCm, int heightCm, int lengthCm, int loadKg, int totalKg) {

	private static final int DEFAULT_HEIGHT_CM = 380;

	static TruckSpec of(Vehicle vehicle) {
		BigDecimal height = vehicle.getHeightM();
		int heightCm = height == null ? DEFAULT_HEIGHT_CM : height.movePointRight(2).intValue();
		return switch (vehicle.getTonnage()) {
			case TON_1 -> new TruckSpec(1, 175, heightCm, 480, 1_000, 3_000);
			case TON_1_4 -> new TruckSpec(1, 175, heightCm, 510, 1_400, 3_500);
			case TON_2_5 -> new TruckSpec(1, 200, heightCm, 600, 2_500, 6_000);
			case TON_3_5 -> new TruckSpec(2, 220, heightCm, 650, 3_500, 8_000);
			case TON_5 -> new TruckSpec(2, 249, heightCm, 850, 5_000, 11_000);
			case TON_5_AXLE -> new TruckSpec(3, 249, heightCm, 1_000, 8_000, 16_000);
			case TON_8 -> new TruckSpec(2, 249, heightCm, 950, 8_000, 16_000);
			case TON_11 -> new TruckSpec(3, 249, heightCm, 1_020, 11_000, 20_000);
			case TON_14 -> new TruckSpec(3, 249, heightCm, 1_100, 14_000, 25_000);
			case TON_18 -> new TruckSpec(3, 249, heightCm, 1_150, 18_000, 30_000);
			case TON_25 -> new TruckSpec(4, 249, heightCm, 1_200, 25_000, 40_000);
		};
	}
}
