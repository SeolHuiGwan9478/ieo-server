package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.restarea.dto.RestAreaListResponse;
import com.ieo.server.domain.restarea.dto.RestAreaResponse;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근처 휴게소 찾기 (TRIP-032). 지금 자리에서 반경 안을 가까운 순으로.
 * 진행 방향으로 "앞쪽"만 고르는 것은 넣지 않았다: 직선거리·방향으로는 분기점 근처에서 다른 고속도로 휴게소가 섞였다
 * (2026-11-11 실제 데이터로 확인). 운행 중 앞쪽 휴게소는 경로 선이 있어야 정확하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestAreaFinder {

	static final int DEFAULT_RADIUS_KM = 50;
	private static final int MAX_RADIUS_KM = 100;
	private static final int MAX_LIMIT = 10;
	private static final double EARTH_RADIUS_M = 6_371_000;

	private final RestAreaRepository repository;

	/** all=false면 라운지(수면실·샤워실·세탁실)가 있는 곳만, true면 화물 휴게소도 넣는다 */
	public RestAreaListResponse findNear(double lat, double lng, int radiusKm, boolean all, int limit) {
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || radiusKm < 1 || radiusKm > MAX_RADIUS_KM
				|| limit < 1 || limit > MAX_LIMIT) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		List<RestAreaResponse> items = repository.findActiveWithin(lat, lng, radiusKm * 1000.0).stream()
				.filter(area -> area.hasTruckLounge() || (all && area.isTruckRestArea()))
				.map(area -> RestAreaResponse.of(area, (int) Math.round(distance(lat, lng, area.latitude(), area.longitude()))))
				.sorted(Comparator.comparingInt(RestAreaResponse::distanceMeters))
				.limit(limit)
				.toList();
		return new RestAreaListResponse(items, RestAreaListResponse.ATTRIBUTION);
	}

	static double distance(double lat1, double lng1, double lat2, double lng2) {
		double p1 = Math.toRadians(lat1);
		double p2 = Math.toRadians(lat2);
		double dLat = p2 - p1;
		double dLng = Math.toRadians(lng2 - lng1);
		double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
	}
}
