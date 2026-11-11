package com.ieo.server.domain.restarea.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한국도로공사 "노선별 휴게시설 현황" (data.ex.co.kr apiId 0317, /openapi/business/serviceAreaRoute).
 * 한 번에 99건까지라 나눠 받는다. 브라우저 표시(User-Agent)가 없으면 400 "Request Blocked"로 막힌다.
 * 한 쪽이라도 실패하면 전부 버리고 빈 목록을 준다: 일부만 저장하면 나머지가 사라진 것으로 보이기 때문이다.
 * 키가 주소에 들어가므로 주소와 예외 내용은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class ExpresswayRestAreaClient implements ExpresswayRestAreaSource {

	static final int PAGE_SIZE = 99;
	private static final int MAX_PAGES = 20;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestAreaProperties properties;
	private final RestClient client;

	public ExpresswayRestAreaClient(RestAreaProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(20));
		this.client = RestClient.builder()
				.requestFactory(factory)
				.defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; ieo-server)")
				.build();
	}

	@Override
	public List<ExpresswayRestArea> fetchAll() {
		if (!properties.hasExpresswayKey()) {
			return List.of();
		}
		try {
			List<ExpresswayRestArea> all = new ArrayList<>();
			for (int page = 1; page <= MAX_PAGES; page++) {
				Page result = parse(fetchPage(page));
				all.addAll(result.rows());
				if (result.rows().isEmpty() || all.size() >= result.total()) {
					return all;
				}
			}
			log.warn("도로공사 휴게소: {}쪽을 넘어 멈춤", MAX_PAGES);
			return List.of();
		} catch (RuntimeException e) {
			log.warn("도로공사 휴게소를 받지 못함: {}", e.getClass().getSimpleName());
			return List.of();
		}
	}

	private String fetchPage(int page) {
		return client.get()
				.uri(properties.expresswayUrl() + "?key={key}&type=json&numOfRows={rows}&pageNo={page}",
						Map.of("key", properties.expresswayKey(), "rows", PAGE_SIZE, "page", page))
				.retrieve()
				.body(String.class);
	}

	record Page(int total, List<ExpresswayRestArea> rows) {
	}

	/** 응답 code가 SUCCESS가 아니면(키 틀림 등) 예외 */
	static Page parse(String body) {
		JsonNode root = JSON.readTree(body == null ? "{}" : body.trim());
		if (!"SUCCESS".equals(root.path("code").asString(""))) {
			throw new IllegalStateException("도로공사 응답 실패: " + root.path("message").asString(""));
		}
		List<ExpresswayRestArea> rows = new ArrayList<>();
		for (JsonNode row : root.path("list")) {
			String code = text(row, "serviceAreaCode");
			String name = text(row, "serviceAreaName");
			if (code == null || name == null) {
				continue;
			}
			String facilities = text(row, "convenience");
			rows.add(new ExpresswayRestArea(
					code,
					name,
					text(row, "routeName"),
					text(row, "direction"),
					facilities == null ? List.of() : Arrays.stream(facilities.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
					"O".equals(text(row, "truckSaYn")),
					text(row, "telNo"),
					text(row, "svarAddr")));
		}
		return new Page(root.path("count").asInt(rows.size()), rows);
	}

	private static String text(JsonNode row, String field) {
		JsonNode node = row.path(field);
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asString("").trim();
		return value.isEmpty() ? null : value;
	}
}
