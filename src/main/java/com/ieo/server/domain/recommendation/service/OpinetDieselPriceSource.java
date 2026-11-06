package com.ieo.server.domain.recommendation.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 오피넷 시도별 주유소 평균가격 (avgSidoPrice.do, D047 자동차용 경유). 한 번 부르면 모든 시도가 온다.
 * 응답은 JSON인데 Content-Type이 text/html이고 앞에 빈 줄이 있어서 문자열로 받아 직접 읽는다.
 * 키가 틀려도 200에 빈 목록으로 온다. 키가 주소에 들어가므로 주소와 예외 내용을 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class OpinetDieselPriceSource implements DieselPriceSource {

	private static final String DIESEL = "D047";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final OpinetProperties properties;
	private final RestClient client;

	public OpinetDieselPriceSource(OpinetProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// 추천 응답 안에서 부르므로 오래 기다리지 않는다
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	@Override
	public Map<String, Integer> fetch() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			return Map.of();
		}
		try {
			String body = client.get()
					.uri(uri -> uri.path("/api/avgSidoPrice.do")
							.queryParam("out", "json")
							.queryParam("prodcd", DIESEL)
							.queryParam("code", properties.apiKey())
							.build())
					.retrieve()
					.body(String.class);
			Map<String, Integer> prices = parse(body);
			if (prices.isEmpty()) {
				log.warn("오피넷 경유가가 비어 있음 (키 확인)");
			}
			return prices;
		} catch (RuntimeException e) {
			// 예외 내용에 키가 든 주소가 있을 수 있어 종류만 남긴다
			log.warn("오피넷 경유가를 받지 못함: {}", e.getClass().getSimpleName());
			return Map.of();
		}
	}

	static Map<String, Integer> parse(String body) {
		JsonNode rows = JSON.readTree(body == null ? "{}" : body.trim()).path("RESULT").path("OIL");
		Map<String, Integer> prices = new HashMap<>();
		for (JsonNode row : rows) {
			double price = row.path("PRICE").asDouble();
			if (price > 0) {
				prices.put(row.path("SIDONM").asString(), (int) Math.round(price));
			}
		}
		return prices;
	}
}
