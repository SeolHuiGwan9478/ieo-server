package com.ieo.server.domain.restarea.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터포털 "전국휴게소정보표준데이터" (tn_pubr_public_rest_area_api). 좌표와 상하행만 쓴다.
 * 이용허락범위는 저작자표시: 앱의 데이터 출처에 적는다.
 * 일부만 받으면 좌표가 비어 보이므로, 한 쪽이라도 실패하면 빈 목록을 준다.
 * 키에 +, /, = 가 있을 수 있어 주소 변수로 넘겨 인코딩한다. 키가 든 주소와 예외 내용은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class PublicRestAreaClient implements RestAreaLocationSource {

	static final int PAGE_SIZE = 1000;
	private static final int MAX_PAGES = 10;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestAreaProperties properties;
	private final RestClient client;

	public PublicRestAreaClient(RestAreaProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.client = RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public List<RestAreaLocation> fetchAll() {
		if (!properties.hasPublicDataKey()) {
			return List.of();
		}
		try {
			List<RestAreaLocation> all = new ArrayList<>();
			for (int page = 1; page <= MAX_PAGES; page++) {
				Page result = parse(fetchPage(page));
				all.addAll(result.rows());
				if (result.read() == 0 || page * PAGE_SIZE >= result.total()) {
					return all;
				}
			}
			log.warn("휴게소 표준데이터: {}쪽을 넘어 멈춤", MAX_PAGES);
			return List.of();
		} catch (RuntimeException e) {
			log.warn("휴게소 표준데이터를 받지 못함: {}", e.getClass().getSimpleName());
			return List.of();
		}
	}

	private String fetchPage(int page) {
		return client.get()
				.uri(properties.publicDataUrl() + "?serviceKey={key}&type=json&pageNo={page}&numOfRows={rows}",
						Map.of("key", properties.publicDataKey(), "page", page, "rows", PAGE_SIZE))
				.retrieve()
				.body(String.class);
	}

	/** read는 이번 쪽에서 읽은 줄 수 (좌표가 없어 뺀 줄 포함) */
	record Page(int total, int read, List<RestAreaLocation> rows) {
	}

	/** resultCode가 00이 아니면(키 틀림, 한도 초과 등) 예외 */
	static Page parse(String body) {
		JsonNode root = JSON.readTree(body == null ? "{}" : body.trim());
		String resultCode = root.path("response").path("header").path("resultCode").asString(
				root.path("header").path("resultCode").asString(""));
		if (!"00".equals(resultCode)) {
			throw new IllegalStateException("표준데이터 응답 실패: " + resultCode);
		}
		JsonNode bodyNode = root.has("response") ? root.path("response").path("body") : root.path("body");
		JsonNode items = bodyNode.path("items").path("item");
		List<RestAreaLocation> rows = new ArrayList<>();
		int read = 0;
		for (JsonNode item : items.isArray() ? items : List.of(items)) {
			if (item.isMissingNode()) {
				continue;
			}
			read++;
			String name = item.path("entrpsNm").asString("").trim();
			Double latitude = number(item.path("latitude"));
			Double longitude = number(item.path("longitude"));
			if (name.isEmpty() || latitude == null || longitude == null) {
				continue;
			}
			rows.add(new RestAreaLocation(name, emptyToNull(item.path("roadRouteDrc").asString("")), latitude, longitude));
		}
		return new Page(bodyNode.path("totalCount").asInt(read), read, rows);
	}

	private static Double number(JsonNode node) {
		try {
			String value = node.asString("").trim();
			return value.isEmpty() ? null : Double.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
