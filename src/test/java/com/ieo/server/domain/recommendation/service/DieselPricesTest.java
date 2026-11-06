package com.ieo.server.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DieselPricesTest {

	private static final String GYEONGGI = "경기 평택시 포승읍";

	private final Deque<Map<String, Integer>> answers = new ArrayDeque<>();
	private int fetches = 0;
	private final DieselPriceSource source = () -> {
		fetches++;
		return answers.isEmpty() ? Map.of() : answers.poll();
	};
	private Instant now = Instant.parse("2026-11-06T00:00:00Z");
	private final Clock clock = new Clock() {
		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	};
	private final DieselPrices prices = new DieselPrices(source,
			new RecommendationProperties(1600, 1.3, 60, 60, 60), clock);

	@Test
	void 주소의_시도_가격을_쓰고_시도를_모르면_전국_평균을_쓴다() {
		answers.add(Map.of("경기", 1836, "전국", 1842));

		assertThat(prices.wonPerLiter(GYEONGGI)).isEqualTo(1836);
		assertThat(prices.wonPerLiter("어딘가 모르는 곳")).isEqualTo(1842);
		assertThat(prices.wonPerLiter(null)).isEqualTo(1842);
	}

	@Test
	void 한_시간_동안은_다시_받지_않는다() {
		answers.add(Map.of("경기", 1836));
		answers.add(Map.of("경기", 1900));

		prices.wonPerLiter(GYEONGGI);
		now = now.plus(Duration.ofMinutes(59));
		assertThat(prices.wonPerLiter(GYEONGGI)).isEqualTo(1836);
		assertThat(fetches).isEqualTo(1);

		now = now.plus(Duration.ofMinutes(1));
		assertThat(prices.wonPerLiter(GYEONGGI)).isEqualTo(1900);
		assertThat(fetches).isEqualTo(2);
	}

	@Test
	void 못_받으면_마지막_값을_쓰고_십분_뒤에_다시_시도한다() {
		answers.add(Map.of("경기", 1836));
		prices.wonPerLiter(GYEONGGI);

		now = now.plus(DieselPrices.REFRESH_AFTER);
		assertThat(prices.wonPerLiter(GYEONGGI)).isEqualTo(1836);
		assertThat(fetches).isEqualTo(2);

		now = now.plus(Duration.ofMinutes(9));
		prices.wonPerLiter(GYEONGGI);
		assertThat(fetches).isEqualTo(2);

		now = now.plus(Duration.ofMinutes(1));
		prices.wonPerLiter(GYEONGGI);
		assertThat(fetches).isEqualTo(3);
	}

	@Test
	void 받은_적이_없으면_설정값으로_계산한다() {
		assertThat(prices.wonPerLiter(GYEONGGI)).isEqualTo(1600);
	}

	@Test
	void 주소_표기가_달라도_오피넷_시도_이름으로_바꾼다() {
		assertThat(DieselPrices.sidoOf("경기도 평택시")).isEqualTo("경기");
		assertThat(DieselPrices.sidoOf("경기 광주시 초월읍")).isEqualTo("경기");
		assertThat(DieselPrices.sidoOf("서울특별시 강서구")).isEqualTo("서울");
		assertThat(DieselPrices.sidoOf("강원특별자치도 원주시")).isEqualTo("강원");
		assertThat(DieselPrices.sidoOf("충청북도 청주시")).isEqualTo("충북");
		assertThat(DieselPrices.sidoOf("경상남도 김해시")).isEqualTo("경남");
		assertThat(DieselPrices.sidoOf("전북특별자치도 군산시")).isEqualTo("전북");
		assertThat(DieselPrices.sidoOf("전라남도 여수시")).isEqualTo("전남광주");
		assertThat(DieselPrices.sidoOf("광주광역시 광산구")).isEqualTo("전남광주");
		assertThat(DieselPrices.sidoOf("세종특별자치시")).isEqualTo("세종");
	}

	@Test
	void 오피넷_응답을_읽는다_앞의_빈_줄과_빈_목록도() {
		Map<String, Integer> parsed = OpinetDieselPriceSource.parse("""

				{"RESULT":
				{"OIL":[
				{"SIDOCD":"00","SIDONM":"전국","PRODCD":"D047","PRICE":1841.83,"DIFF":1841.83},

				{"SIDOCD":"02","SIDONM":"경기","PRODCD":"D047","PRICE":1836.23,"DIFF":-0.39}
				]} }
				""");

		assertThat(parsed).containsEntry("전국", 1842).containsEntry("경기", 1836);
		assertThat(OpinetDieselPriceSource.parse("{\"RESULT\": {\"OIL\":[ ]} }")).isEmpty();
	}
}
