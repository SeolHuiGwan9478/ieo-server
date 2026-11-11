package com.ieo.server.domain.restarea.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 2026-11-11에 받은 TMAP 화물차 경로 응답 모양으로 읽기 확인 */
class TmapTruckRouteLineClientTest {

	@Test
	void 선_좌표만_index_순서로_잇고_겹치는_점은_한_번만_두고_졸음쉼터를_꺼낸다() {
		String body = """
				{"type":"FeatureCollection","features":[
				 {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127.401,36.599],[127.402,36.598]]},
				  "properties":{"index":3,"lineIndex":1,"name":"경부 고속도로","distance":120}},
				 {"type":"Feature","geometry":{"type":"Point","coordinates":[127.4006,36.5996]},
				  "properties":{"index":0,"pointIndex":0,"totalDistance":289314,"turnType":200,"pointType":"S"}},
				 {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127.4006,36.5996],[127.401,36.599]]},
				  "properties":{"index":1,"lineIndex":0,"name":"서부로841번길","distance":104}},
				 {"type":"Feature","geometry":{"type":"Point","coordinates":[127.6567,36.4732]},
				  "properties":{"index":2,"pointIndex":1,"name":"수한졸음쉼터","turnType":150,"pointType":"N"}}
				]}
				""";

		var route = TmapTruckRouteLineClient.parse(body).orElseThrow();

		assertThat(route.points()).hasSize(3);
		assertThat(route.points().getFirst()).containsExactly(36.5996, 127.4006);
		assertThat(route.points().getLast()).containsExactly(36.598, 127.402);
		assertThat(route.shelters()).singleElement().satisfies(shelter -> {
			assertThat(shelter.name()).isEqualTo("수한졸음쉼터");
			assertThat(shelter.latitude()).isEqualTo(36.4732);
		});
	}

	@Test
	void 선이_없으면_경로가_없다() {
		assertThat(TmapTruckRouteLineClient.parse("{\"features\":[]}")).isEmpty();
		assertThat(TmapTruckRouteLineClient.parse("{\"error\":{\"code\":\"INVALID_API_KEY\"}}")).isEmpty();
	}
}
