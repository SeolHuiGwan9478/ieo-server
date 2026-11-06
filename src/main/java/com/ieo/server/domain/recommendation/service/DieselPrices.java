package com.ieo.server.domain.recommendation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공차 연료비에 쓰는 경유 1L 가격 (REC-011). 공차가 시작하는 곳의 시도 평균을 쓴다.
 * 메모리에 1시간 들고 있다가 필요할 때 새로 받는다. 오피넷 가격은 하루 6번 바뀌고 무료 키는 하루 300번까지다.
 * 못 받으면 마지막 값을 쓰고 10분 뒤 다시 시도한다. 받은 적이 없으면 설정값으로 계산해 추천은 멈추지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DieselPrices {

	static final Duration REFRESH_AFTER = Duration.ofHours(1);
	static final Duration RETRY_AFTER = Duration.ofMinutes(10);
	private static final String NATIONWIDE = "전국";
	/** 전남과 광주는 오피넷에서 한 지역으로 온다 */
	private static final String JEONNAM_GWANGJU = "전남광주";

	private final DieselPriceSource source;
	private final RecommendationProperties properties;
	private final Clock clock;

	private Map<String, Integer> prices = Map.of();
	private Instant nextFetchAt = Instant.MIN;

	/** 이 주소가 있는 시도의 평균 경유가(원). 시도를 모르면 전국 평균 */
	public synchronized int wonPerLiter(String address) {
		refreshIfDue();
		Integer regional = prices.get(sidoOf(address));
		if (regional != null) {
			return regional;
		}
		return prices.getOrDefault(NATIONWIDE, properties.dieselPriceWon());
	}

	private void refreshIfDue() {
		Instant now = clock.instant();
		if (now.isBefore(nextFetchAt)) {
			return;
		}
		Map<String, Integer> fetched = source.fetch();
		if (fetched.isEmpty()) {
			nextFetchAt = now.plus(RETRY_AFTER);
			return;
		}
		prices = fetched;
		nextFetchAt = now.plus(REFRESH_AFTER);
	}

	/** 주소 첫 낱말에서 오피넷 시도 이름을 만든다: "경기도 평택시" → 경기, "충청북도" → 충북, "강원특별자치도" → 강원 */
	static String sidoOf(String address) {
		if (address == null || address.isBlank()) {
			return NATIONWIDE;
		}
		String first = address.trim().split("\\s+")[0];
		if (first.startsWith("광주") || first.startsWith("전남") || first.startsWith("전라남")) {
			return JEONNAM_GWANGJU;
		}
		if (first.startsWith("전라북")) {
			return "전북";
		}
		if (first.startsWith("충청북")) {
			return "충북";
		}
		if (first.startsWith("충청남")) {
			return "충남";
		}
		if (first.startsWith("경상북")) {
			return "경북";
		}
		if (first.startsWith("경상남")) {
			return "경남";
		}
		return first.length() < 2 ? NATIONWIDE : first.substring(0, 2);
	}
}
