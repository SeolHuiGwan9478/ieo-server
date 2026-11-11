package com.ieo.server.domain.restarea.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 실제 응답 모양(2026-11-11에 받아 본 것)으로 읽기 확인 */
class RestAreaClientParseTest {

	@Test
	void 도로공사_응답에서_편의시설과_화물_휴게소를_읽고_이름_없는_줄은_뺀다() {
		String body = """
				{"count":221,"list":[
				 {"direction":"부산","svarAddr":"충북 청원군 옥산면오산리 589-15","routeName":"경부선","telNo":"043-260-1053",
				  "serviceAreaCode":"A00032","serviceAreaName":"옥산(부산)휴게소","convenience":"수유실|수면실|샤워실|세탁실|쉼터|이발소",
				  "maintenanceYn":"O","truckSaYn":"O"},
				 {"direction":"청원","svarAddr":null,"routeName":null,"telNo":"043-544-1466","serviceAreaCode":"A00195",
				  "serviceAreaName":null,"convenience":"수유실","truckSaYn":"X"}
				],"code":"SUCCESS","message":"인증키가 유효합니다."}
				""";

		ExpresswayRestAreaClient.Page page = ExpresswayRestAreaClient.parse(body);

		assertThat(page.total()).isEqualTo(221);
		assertThat(page.rows()).hasSize(1);
		ExpresswayRestAreaSource.ExpresswayRestArea row = page.rows().getFirst();
		assertThat(row.code()).isEqualTo("A00032");
		assertThat(row.facilities()).containsExactly("수유실", "수면실", "샤워실", "세탁실", "쉼터", "이발소");
		assertThat(row.truckRestArea()).isTrue();
		assertThat(row.direction()).isEqualTo("부산");
	}

	@Test
	void 도로공사_키가_틀리면_실패로_본다() {
		assertThatThrownBy(() -> ExpresswayRestAreaClient.parse("{\"code\":\"ERROR\",\"message\":\"인증키가 유효하지 않습니다.\"}"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 표준데이터에서_좌표와_상하행을_읽는다() {
		String body = """
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[
				 {"entrpsNm":"옥산(부산)","roadRouteNm":"경부선","roadRouteDrc":"하행","latitude":"36.657843","longitude":"127.370066"},
				 {"entrpsNm":"좌표없음(서울)","roadRouteDrc":"상행","latitude":"","longitude":""}
				]},"numOfRows":"1000","pageNo":"1","totalCount":"215"}}}
				""";

		PublicRestAreaClient.Page page = PublicRestAreaClient.parse(body);

		assertThat(page.total()).isEqualTo(215);
		assertThat(page.read()).isEqualTo(2);
		assertThat(page.rows()).singleElement().satisfies(row -> {
			assertThat(row.name()).isEqualTo("옥산(부산)");
			assertThat(row.roadDirection()).isEqualTo("하행");
			assertThat(row.latitude()).isEqualTo(36.657843);
		});
	}

	@Test
	void 표준데이터_헤더만_있는_모양도_읽고_오류_코드는_실패로_본다() {
		String ok = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":[]},\"totalCount\":\"0\"}}";
		assertThat(PublicRestAreaClient.parse(ok).rows()).isEmpty();
		assertThatThrownBy(() -> PublicRestAreaClient.parse("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}"))
				.isInstanceOf(IllegalStateException.class);
	}
}
