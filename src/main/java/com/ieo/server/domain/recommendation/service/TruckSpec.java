package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;

/**
 * TMAP에 넘기는 차량 정보. 폭·길이·총중량은 기사가 입력한 값을 쓰고, 비어 있으면 톤수별 기본값을 쓴다.
 * carType은 고속도로 통행료 차종 번호다(1~5종). 실제 차종은 축 수·윤폭·윤거로 정해진다. 축 수가 있으면 그것을 먼저 따르고, 없으면 톤수로 어림한다 (docs/design/04_배차추천.md)
 */
record TruckSpec(int carType, int widthCm, int heightCm, int lengthCm, int loadKg, int totalKg) {

	private static final int DEFAULT_HEIGHT_CM = 380;

	static TruckSpec of(Vehicle vehicle) {
		BigDecimal height = vehicle.getHeightM();
		int heightCm = height == null ? DEFAULT_HEIGHT_CM : height.movePointRight(2).intValue();
		TruckSpec defaults = switch (vehicle.getTonnage()) {
			case TON_1 -> new TruckSpec(1, 175, heightCm, 480, 1_000, 3_000);
			case TON_1_4 -> new TruckSpec(1, 175, heightCm, 510, 1_400, 3_500);
			case TON_2_5 -> new TruckSpec(2, 200, heightCm, 600, 2_500, 6_000);
			case TON_3_5 -> new TruckSpec(2, 220, heightCm, 650, 3_500, 8_000);
			case TON_5 -> new TruckSpec(2, 249, heightCm, 850, 5_000, 11_000);
			case TON_5_AXLE -> new TruckSpec(4, 249, heightCm, 1_000, 8_000, 16_000);
			case TON_8 -> new TruckSpec(3, 249, heightCm, 950, 8_000, 16_000);
			case TON_11 -> new TruckSpec(4, 249, heightCm, 1_020, 11_000, 20_000);
			case TON_14 -> new TruckSpec(4, 249, heightCm, 1_100, 14_000, 25_000);
			case TON_18 -> new TruckSpec(4, 249, heightCm, 1_150, 18_000, 30_000);
			case TON_25 -> new TruckSpec(5, 249, heightCm, 1_200, 25_000, 40_000);
		};
		int totalKg = vehicle.getTotalWeightKg() == null ? defaults.totalKg() : vehicle.getTotalWeightKg();
		return new TruckSpec(tollClass(defaults.carType(), vehicle.getAxleCount()),
				vehicle.getWidthCm() == null ? defaults.widthCm() : vehicle.getWidthCm(),
				heightCm,
				vehicle.getLengthCm() == null ? defaults.lengthCm() : vehicle.getLengthCm(),
				Math.min(defaults.loadKg(), totalKg),
				totalKg);
	}

	/** 3축은 4종, 4축 이상은 5종이다. 2축은 3종까지이고 그 안의 구분은 윤거가 필요해 톤수를 따른다 */
	private static int tollClass(int byTonnage, Integer axleCount) {
		if (axleCount == null) {
			return byTonnage;
		}
		return switch (axleCount) {
			case 2 -> Math.min(byTonnage, 3);
			case 3 -> 4;
			default -> 5;
		};
	}
}
