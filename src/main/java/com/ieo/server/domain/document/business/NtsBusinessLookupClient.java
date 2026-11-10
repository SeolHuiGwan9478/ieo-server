package com.ieo.server.domain.document.business;

import com.ieo.server.domain.document.enums.BusinessStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 국세청 사업자등록정보 진위확인 API (POST /api/nts-businessman/v1/validate).
 * 요청: businesses[{b_no, start_dt(YYYYMMDD), p_nm}]. 응답: data[{valid(01 일치·02 불일치), valid_msg, status{b_stt_cd, tax_type, end_dt}}].
 * 키는 serviceKey 쿼리로 넘긴다. Decoding 키를 URI 변수로 넣어 +, /, = 가 제대로 인코딩되게 한다.
 * 키가 주소에 들어가므로 주소와 예외 내용을 로그에 남기지 않는다. 응답이 이상하면 ERROR 결과로 돌려주고 예외를 내지 않는다.
 */
@Slf4j
public class NtsBusinessLookupClient implements BusinessLookupClient {

	static final String VALIDATE_PATH = "/api/nts-businessman/v1/validate";
	private static final DateTimeFormatter NTS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final NtsProperties properties;
	private final RestClient client;

	public NtsBusinessLookupClient(NtsProperties properties) {
		this(properties, RestClient.builder().requestFactory(requestFactory()));
	}

	/** 테스트가 MockRestServiceServer를 묶은 builder를 넘긴다 */
	NtsBusinessLookupClient(NtsProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.client = builder.baseUrl(properties.baseUrl()).build();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// 서류 제출 요청 안에서 부르므로 오래 기다리지 않는다
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		return factory;
	}

	@Override
	public BusinessLookup lookup(String businessNumber, LocalDate openedOn, String ownerName) {
		if (!properties.hasKey()) {
			return BusinessLookup.skipped("국세청 API 키가 없어 조회하지 않았어요");
		}
		String request = JSON.writeValueAsString(Map.of("businesses", List.of(Map.of(
				"b_no", businessNumber,
				"start_dt", NTS_DATE.format(openedOn),
				"p_nm", ownerName))));
		try {
			String body = client.post()
					.uri(uri -> uri.path(VALIDATE_PATH).queryParam("serviceKey", "{key}").build(properties.serviceKey()))
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(String.class);
			return parse(body);
		} catch (RuntimeException e) {
			// 예외 내용에 키가 든 주소가 있을 수 있어 종류만 남긴다
			log.warn("국세청 진위확인을 받지 못함: {}", e.getClass().getSimpleName());
			return BusinessLookup.error("국세청이 응답하지 않았어요 (" + e.getClass().getSimpleName() + ")");
		}
	}

	static BusinessLookup parse(String body) {
		JsonNode root = JSON.readTree(body == null ? "{}" : body);
		JsonNode data = root.path("data");
		if (!data.isArray() || data.isEmpty()) {
			String message = root.path("msg").asString();
			log.warn("국세청 진위확인 응답에 결과가 없음: status_code={}", root.path("status_code").asString());
			return BusinessLookup.error(message.isBlank() ? "국세청 응답에 결과가 없어요" : "국세청 응답: " + message);
		}
		JsonNode item = data.get(0);
		JsonNode status = item.path("status");
		if (!"01".equals(item.path("valid").asString())) {
			String message = item.path("valid_msg").asString();
			// 번호 자체가 등록되지 않았으면 tax_type에 그 설명이 온다
			String taxType = status.path("tax_type").asString();
			if (status.path("b_stt_cd").asString().isBlank() && !taxType.isBlank()) {
				message = taxType;
			}
			return BusinessLookup.mismatch(message.isBlank() ? "국세청 등록 정보와 달라요" : message, false);
		}
		return BusinessLookup.match(
				BusinessStatus.fromCode(status.path("b_stt_cd").asString()),
				blankToNull(status.path("tax_type").asString()),
				parseDate(status.path("end_dt").asString()),
				false);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static LocalDate parseDate(String yyyymmdd) {
		if (yyyymmdd == null || yyyymmdd.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(yyyymmdd, NTS_DATE);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
