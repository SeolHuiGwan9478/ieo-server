package com.ieo.server.domain.document.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 국세청 진위확인 API 호출과 응답 해석. 실제 국세청 대신 MockRestServiceServer가 응답한다 (키 없이 돈다) */
class NtsBusinessLookupClientTest {

	/** 포털이 주는 Decoding 키에는 +, /, = 가 들어 있다. 주소에서 %2B %2F %3D 로 인코딩돼야 국세청이 받아 준다 */
	private static final String KEY = "abc+/def==";
	private static final String VALIDATE_URL = "https://api.odcloud.kr/api/nts-businessman/v1/validate?serviceKey=abc%2B%2Fdef%3D%3D";

	private static final String MATCH = """
			{"status_code":"OK","request_cnt":1,"valid_cnt":1,"data":[{"b_no":"1234567800","valid":"01","valid_msg":"",
			 "request_param":{"b_no":"1234567800","start_dt":"20200131","p_nm":"김기사"},
			 "status":{"b_no":"1234567800","b_stt":"폐업자","b_stt_cd":"03","tax_type":"부가가치세 일반과세자","tax_type_cd":"01",
			  "end_dt":"20240630","utcc_yn":"N","tax_type_change_dt":"","invoice_apply_dt":"","rbf_tax_type":"해당없음","rbf_tax_type_cd":"99"}}]}
			""";
	private static final String MISMATCH = """
			{"status_code":"OK","request_cnt":1,"valid_cnt":0,"data":[{"b_no":"1234567891","valid":"02","valid_msg":"확인할 수 없습니다.",
			 "request_param":{"b_no":"1234567891","start_dt":"20200131","p_nm":"김기사"},
			 "status":{"b_no":"1234567891","b_stt":"","b_stt_cd":"","tax_type":"국세청에 등록되지 않은 사업자등록번호입니다.","tax_type_cd":"","end_dt":""}}]}
			""";

	private MockRestServiceServer server;
	private NtsBusinessLookupClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NtsBusinessLookupClient(new NtsProperties(KEY, "https://api.odcloud.kr", false), builder);
	}

	@Test
	void 번호_개업일_대표자를_보내고_일치하면_사업자_상태와_과세_유형을_읽는다() {
		server.expect(requestTo(VALIDATE_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.businesses[0].b_no").value("1234567800"))
				.andExpect(jsonPath("$.businesses[0].start_dt").value("20200131"))
				.andExpect(jsonPath("$.businesses[0].p_nm").value("김기사"))
				.andRespond(withSuccess(MATCH, MediaType.APPLICATION_JSON));

		BusinessLookup lookup = client.lookup("1234567800", LocalDate.of(2020, 1, 31), "김기사");

		assertEquals(BusinessCheckResult.MATCH, lookup.result());
		assertEquals(BusinessStatus.CLOSED, lookup.status());
		assertEquals("부가가치세 일반과세자", lookup.taxType());
		assertEquals(LocalDate.of(2024, 6, 30), lookup.closedOn());
		assertFalse(lookup.stub());
		server.verify();
	}

	@Test
	void 등록되지_않은_번호면_불일치와_국세청_설명이_온다() {
		server.expect(requestTo(VALIDATE_URL)).andRespond(withSuccess(MISMATCH, MediaType.APPLICATION_JSON));

		BusinessLookup lookup = client.lookup("1234567891", LocalDate.of(2020, 1, 31), "김기사");

		assertEquals(BusinessCheckResult.MISMATCH, lookup.result());
		assertNull(lookup.status());
		assertEquals("국세청에 등록되지 않은 사업자등록번호입니다.", lookup.message());
	}

	@Test
	void 국세청이_오류를_돌려주면_예외_없이_조회_실패_결과다() {
		server.expect(requestTo(VALIDATE_URL)).andRespond(withServerError());

		BusinessLookup lookup = client.lookup("1234567800", LocalDate.of(2020, 1, 31), "김기사");

		assertEquals(BusinessCheckResult.ERROR, lookup.result());
	}

	@Test
	void 키가_없으면_부르지_않고_조회_안_함이다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();
		NtsBusinessLookupClient noKey = new NtsBusinessLookupClient(new NtsProperties("", "https://api.odcloud.kr", false), builder);

		assertEquals(BusinessCheckResult.SKIPPED, noKey.lookup("1234567800", LocalDate.of(2020, 1, 31), "김기사").result());
		strict.verify();
	}
}
