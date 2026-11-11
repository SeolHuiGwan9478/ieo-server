package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.RouteLine;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.Shelter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 경로 위 쉴 곳 고르기 (docs/design/08_휴게소.md 3-3).
 * 1. 경로 좌표를 m 평면으로 바꾸고 처음부터 누적 거리를 만든다.
 * 2. 라운지마다 모든 선분에 수직으로 내려 가장 가까운 점을 찾는다. 수직 발이 선분 밖이면 가까운 끝점까지 잰다.
 * 3. 경로에서 {@link #MAX_OFFSET_METERS} 안이고, 가는 방향 오른쪽(우측 통행)이고, 경로 거리 {@link #MIN_AHEAD_METERS} 이상만 남긴다.
 * 4. 졸음쉼터는 TMAP이 경로 위로 준 것이라 거리만 잰다.
 * 경로가 지금 위치에서 시작하므로 경로 거리가 곧 "몇 km 앞"이다.
 */
public final class RouteRestAreaPicker {

	/** 경로에서 이만큼 안이면 경로 위 휴게소다. 실제로는 60~140m였다 */
	static final double MAX_OFFSET_METERS = 500;
	/** 이보다 가까우면 진입로를 지났거나 방금 지나친 곳이다 */
	static final double MIN_AHEAD_METERS = 2_000;
	private static final double EARTH_RADIUS_M = 6_371_000;
	private static final double BOX_MARGIN_DEGREES = 0.01;

	private RouteRestAreaPicker() {
	}

	public enum Kind { LOUNGE, DROWSY_SHELTER }

	/** 경로 위 쉴 곳. lounge면 [area]가, 졸음쉼터면 [shelter]가 있다 */
	public record Pick(Kind kind, RestArea area, Shelter shelter, int aheadMeters, int offsetMeters) {
	}

	public static List<Pick> pick(RouteLine route, List<RestArea> lounges) {
		Plane plane = new Plane(route.points());
		double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE, minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
		for (double[] p : route.points()) {
			minLat = Math.min(minLat, p[0]);
			maxLat = Math.max(maxLat, p[0]);
			minLng = Math.min(minLng, p[1]);
			maxLng = Math.max(maxLng, p[1]);
		}
		List<Pick> picks = new ArrayList<>();
		for (RestArea area : lounges) {
			Double lat = area.latitude();
			Double lng = area.longitude();
			if (lat == null || lng == null || lat < minLat - BOX_MARGIN_DEGREES || lat > maxLat + BOX_MARGIN_DEGREES
					|| lng < minLng - BOX_MARGIN_DEGREES || lng > maxLng + BOX_MARGIN_DEGREES) {
				continue;
			}
			Nearest nearest = plane.nearest(lat, lng);
			if (nearest.offset() <= MAX_OFFSET_METERS && nearest.right() && nearest.along() >= MIN_AHEAD_METERS) {
				picks.add(new Pick(Kind.LOUNGE, area, null, (int) Math.round(nearest.along()), (int) Math.round(nearest.offset())));
			}
		}
		for (Shelter shelter : route.shelters()) {
			Nearest nearest = plane.nearest(shelter.latitude(), shelter.longitude());
			if (nearest.along() >= MIN_AHEAD_METERS) {
				picks.add(new Pick(Kind.DROWSY_SHELTER, null, shelter, (int) Math.round(nearest.along()), (int) Math.round(nearest.offset())));
			}
		}
		picks.sort(Comparator.comparingInt(Pick::aheadMeters));
		return picks;
	}

	/**
	 * @param offset 경로까지 거리
	 * @param along  경로 시작부터 가장 가까운 점까지 경로를 따라간 거리
	 * @param right  가는 방향 오른쪽에 있다
	 */
	record Nearest(double offset, double along, boolean right) {
	}

	/** 경로 둘레에서는 위도·경도를 m 평면으로 펴도 수백 m 판단에 충분하다 */
	static final class Plane {
		private final double metersPerLat;
		private final double metersPerLng;
		private final double[] xs;
		private final double[] ys;
		private final double[] cumulative;

		Plane(List<double[]> points) {
			double meanLat = points.stream().mapToDouble(p -> p[0]).average().orElse(36);
			metersPerLat = Math.toRadians(1) * EARTH_RADIUS_M;
			metersPerLng = metersPerLat * Math.cos(Math.toRadians(meanLat));
			int n = points.size();
			xs = new double[n];
			ys = new double[n];
			cumulative = new double[n];
			for (int i = 0; i < n; i++) {
				xs[i] = points.get(i)[1] * metersPerLng;
				ys[i] = points.get(i)[0] * metersPerLat;
				if (i > 0) {
					cumulative[i] = cumulative[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
				}
			}
		}

		Nearest nearest(double lat, double lng) {
			double px = lng * metersPerLng;
			double py = lat * metersPerLat;
			double best = Double.MAX_VALUE;
			double bestAlong = 0;
			double bestCross = 0;
			for (int i = 0; i < xs.length - 1; i++) {
				double dx = xs[i + 1] - xs[i];
				double dy = ys[i + 1] - ys[i];
				double lengthSquared = dx * dx + dy * dy;
				if (lengthSquared == 0) {
					continue;
				}
				// 수직 발의 위치. 선분 밖이면 가까운 끝점으로 자른다
				double t = Math.max(0, Math.min(1, ((px - xs[i]) * dx + (py - ys[i]) * dy) / lengthSquared));
				double distance = Math.hypot(px - (xs[i] + t * dx), py - (ys[i] + t * dy));
				if (distance < best) {
					best = distance;
					bestAlong = cumulative[i] + t * Math.sqrt(lengthSquared);
					// 외적이 음수면 가는 방향 오른쪽
					bestCross = dx * (py - ys[i]) - dy * (px - xs[i]);
				}
			}
			return new Nearest(best, bestAlong, bestCross < 0);
		}
	}
}
