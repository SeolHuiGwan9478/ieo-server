package com.ieo.server.domain.restarea.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.service.RouteRestAreaPicker.Kind;
import com.ieo.server.domain.restarea.service.RouteRestAreaPicker.Pick;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.RouteLine;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.Shelter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 남쪽으로 곧게 가는 33km 경로 (경도 127.40, 위도 36.60 → 36.30) */
class RouteRestAreaPickerTest {

	private static final List<String> LOUNGE = List.of("수면실", "샤워실", "세탁실");

	/** 고속도로 직선 구간처럼 좌표가 듬성듬성하다: 3km마다 하나 */
	private static RouteLine southbound(List<Shelter> shelters) {
		List<double[]> points = new ArrayList<>();
		for (int i = 0; i <= 11; i++) {
			points.add(new double[] {36.60 - i * 0.027, 127.40});
		}
		return new RouteLine(points, shelters);
	}

	private static RestArea lounge(String code, String name, double lat, double lng) {
		return RestArea.create(new RestAreaSnapshot(code, name, "경부선", "부산", "하행", LOUNGE, true, null, null, lat, lng),
				Instant.parse("2026-11-11T00:00:00Z"));
	}

	@Test
	void 경로_오른쪽_500m_안만_남기고_경로_거리순으로_졸음쉼터와_함께() {
		RestArea right = lounge("R", "오른쪽(부산)휴게소", 36.50, 127.399);       // 서쪽 90m: 남쪽으로 갈 때 오른쪽
		RestArea left = lounge("L", "왼쪽(서울)휴게소", 36.50, 127.401);          // 동쪽 90m: 반대편
		RestArea far = lounge("F", "먼곳(부산)휴게소", 36.45, 127.38);            // 서쪽 1.8km
		RestArea behind = lounge("B", "방금(부산)휴게소", 36.605, 127.399);       // 출발점 뒤 600m
		RouteLine route = southbound(List.of(new Shelter("중간졸음쉼터", 36.40, 127.40)));

		List<Pick> picks = RouteRestAreaPicker.pick(route, List.of(right, left, far, behind));

		assertThat(picks).extracting(Pick::kind).containsExactly(Kind.LOUNGE, Kind.DROWSY_SHELTER);
		assertThat(picks.get(0).area()).isSameAs(right);
		// 위도 0.1도 ≈ 11.1km. 좌표 사이(선분 가운데)에서도 수직 발로 잰다
		assertThat(picks.get(0).aheadMeters()).isBetween(11_000, 11_300);
		assertThat(picks.get(0).offsetMeters()).isBetween(80, 100);
		assertThat(picks.get(1).aheadMeters()).isBetween(22_000, 22_500);
	}

	@Test
	void 바로_앞_2km_안은_진입로를_지났다고_보고_뺀다() {
		RestArea near = lounge("N", "코앞(부산)휴게소", 36.59, 127.399);         // 1.1km 앞
		assertThat(RouteRestAreaPicker.pick(southbound(List.of()), List.of(near))).isEmpty();
	}

	@Test
	void 꺾이는_모서리_바깥쪽은_끝점까지_재서_잡는다() {
		// 남쪽으로 가다가 서쪽으로 꺾는 경로. 모서리 남동쪽 바깥의 점은 두 선분 모두 수직 발이 밖이다
		RouteLine route = new RouteLine(List.of(
				new double[] {36.60, 127.40}, new double[] {36.50, 127.40}, new double[] {36.50, 127.30}), List.of());
		RouteRestAreaPicker.Plane plane = new RouteRestAreaPicker.Plane(route.points());

		RouteRestAreaPicker.Nearest corner = plane.nearest(36.4990, 127.4010);

		assertThat(corner.offset()).isBetween(100.0, 160.0);
		assertThat(corner.along()).isBetween(11_000.0, 11_200.0);
	}
}
