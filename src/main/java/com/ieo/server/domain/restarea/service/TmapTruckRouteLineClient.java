package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.recommendation.service.TmapProperties;
import com.ieo.server.domain.recommendation.service.TruckSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TMAP 화물차 경로안내의 전체 응답(totalValue=1)에서 길 모양과 졸음쉼터만 꺼낸다 (docs/design/08_휴게소.md 3장).
 * features는 점(안내 지점)과 선(도로 구간)이 index 순서로 온다. 선의 좌표만 이어 한 줄로 만든다.
 * 결과는 저장하지 않는다 (TMAP 결과는 24시간까지만 들고 있기로 함). 키가 없거나 실패하면 비어 있다.
 */
@Slf4j
@Component
public class TmapTruckRouteLineClient implements TruckRouteLineSource {

	static final int DROWSY_SHELTER = 150;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final TmapProperties properties;
	private final RestClient client;

	public TmapTruckRouteLineClient(TmapProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(6));
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	@Override
	public Optional<RouteLine> route(double fromLat, double fromLng, double toLat, double toLng, TruckSpec spec) {
		if (properties.appKey() == null || properties.appKey().isBlank()) {
			return Optional.empty();
		}
		try {
			String body = client.post()
					.uri(uri -> uri.path("/tmap/truck/routes").queryParam("version", "1").build())
					.header("appKey", properties.appKey())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(spec.routeRequest(fromLat, fromLng, toLat, toLng, 1))
					.retrieve()
					.body(String.class);
			return parse(body);
		} catch (RuntimeException e) {
			log.warn("TMAP 화물차 경로(선)를 받지 못함: {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	static Optional<RouteLine> parse(String body) {
		JsonNode features = JSON.readTree(body == null ? "{}" : body).path("features");
		List<JsonNode> ordered = StreamSupport.stream(features.spliterator(), false)
				.sorted(Comparator.comparingInt(f -> f.path("properties").path("index").asInt()))
				.toList();
		List<double[]> points = new ArrayList<>();
		List<Shelter> shelters = new ArrayList<>();
		for (JsonNode feature : ordered) {
			JsonNode geometry = feature.path("geometry");
			String type = geometry.path("type").asString("");
			if ("LineString".equals(type)) {
				for (JsonNode coordinate : geometry.path("coordinates")) {
					double[] point = {coordinate.path(1).asDouble(), coordinate.path(0).asDouble()};
					double[] last = points.isEmpty() ? null : points.getLast();
					if (last == null || last[0] != point[0] || last[1] != point[1]) {
						points.add(point);
					}
				}
			} else if ("Point".equals(type) && feature.path("properties").path("turnType").asInt() == DROWSY_SHELTER) {
				JsonNode coordinate = geometry.path("coordinates");
				shelters.add(new Shelter(feature.path("properties").path("name").asString(""),
						coordinate.path(1).asDouble(), coordinate.path(0).asDouble()));
			}
		}
		return points.size() < 2 ? Optional.empty() : Optional.of(new RouteLine(points, shelters));
	}
}
