package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * TMAP에 넘기는 차량 정보. 폭·길이·총중량은 기사가 입력한 값을 쓰고, 비어 있으면 톤수별 기본값을 쓴다.
 * carType은 고속도로 통행료 차종 번호다(1~5종). 실제 차종은 축 수·윤폭·윤거로 정해진다. 축 수가 있으면 그것을 먼저 따르고, 없으면 톤수로 어림한다 (docs/design/04_배차추천.md)
 */
public record TruckSpec(int carType, int widthCm, int heightCm, int lengthCm, int loadKg, int totalKg) {

	private static final int DEFAULT_HEIGHT_CM = 380;

	public static TruckSpec of(Vehicle vehicle) {
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

	/**
	 * TMAP 화물차 경로안내 요청 본문. 좌표는 WGS84(경도 X, 위도 Y).
	 *
	 * @param totalValue 1 = 경로 선까지 전부, 2 = 거리·시간·요금 요약만
	 */
	public Map<String, Object> routeRequest(double fromLat, double fromLng, double toLat, double toLng, int totalValue) {
		Map<String, Object> request = new HashMap<>();
		request.put("startX", fromLng);
		request.put("startY", fromLat);
		request.put("endX", toLng);
		request.put("endY", toLat);
		request.put("startName", "start");
		request.put("endName", "end");
		request.put("reqCoordType", "WGS84GEO");
		request.put("resCoordType", "WGS84GEO");
		request.put("totalValue", totalValue);
		request.put("tollgateFareInfo", "Y");
		request.put("carType", carType);
		request.put("truckType", 1);
		request.put("truckWidth", widthCm);
		request.put("truckHeight", heightCm);
		request.put("truckLength", lengthCm);
		request.put("truckWeight", loadKg);
		request.put("truckTotalWeight", totalKg);
		return request;
	}
}
