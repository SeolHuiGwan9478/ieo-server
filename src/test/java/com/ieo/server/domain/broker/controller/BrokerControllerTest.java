package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerControllerTest {

	/** application-test.yml의 ieo.cors.allowed-origins */
	private static final String CONSOLE_ORIGIN = "http://localhost:5173";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BrokerService brokerService;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private User user;
	private String bearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		user = userRepository.save(User.create("01090003001", UserRole.BROKER));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	@Test
	void 가입한_뒤_회사를_등록하면_대표가_되고_내_회사로_보인다() throws Exception {
		postBroker("{\"name\":\"한빛로지스\",\"businessNumber\":\"123-45-67891\",\"licenseNumber\":\"제2026-001호\","
				+ "\"representativeName\":\"김대표\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.businessNumber").value("1234567891"))
				.andExpect(jsonPath("$.position").value("OWNER"))
				.andExpect(jsonPath("$.settlementDays").value(30));

		mockMvc.perform(get("/api/brokers/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("한빛로지스"));
	}

	@Test
	void 사업자등록번호가_올바르지_않으면_400() throws Exception {
		postBroker("{\"name\":\"한빛로지스\",\"businessNumber\":\"1234567890\",\"licenseNumber\":\"제2026-001호\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BROKER_400_BUSINESS_NUMBER"));
	}

	@Test
	void 허가_번호가_없으면_400() throws Exception {
		postBroker("{\"name\":\"한빛로지스\",\"businessNumber\":\"1234567891\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("licenseNumber"));
	}

	@Test
	void 이미_등록된_회사면_409() throws Exception {
		User other = userRepository.save(User.create("01090003003", UserRole.BROKER));
		brokerService.register(other.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));

		postBroker("{\"name\":\"한빛\",\"businessNumber\":\"1234567891\",\"licenseNumber\":\"제2026-001호\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_409_BUSINESS_NUMBER"));
	}

	@Test
	void 로그인하지_않으면_회사를_등록할_수_없다() throws Exception {
		mockMvc.perform(post("/api/brokers").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"한빛로지스\",\"businessNumber\":\"1234567891\",\"licenseNumber\":\"제2026-001호\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 내_회사와_직책을_돌려준다() throws Exception {
		brokerService.register(user.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));

		mockMvc.perform(get("/api/brokers/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("한빛로지스"))
				.andExpect(jsonPath("$.licenseNumber").value("제2026-001호"))
				.andExpect(jsonPath("$.position").value("OWNER"))
				.andExpect(jsonPath("$.positionLabel").value("대표"));
	}

	@Test
	void 소속이_없으면_404_BROKER_404_MEMBER() throws Exception {
		mockMvc.perform(get("/api/brokers/me").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
	}

	@Test
	void 차주_계정은_403() throws Exception {
		User driver = userRepository.save(User.create("01090003002", UserRole.DRIVER));

		mockMvc.perform(get("/api/brokers/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(driver.getId()).accessToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_ROLE"));
	}

	@Test
	void 콘솔_주소의_CORS_사전_요청은_토큰_없이_허용한다() throws Exception {
		mockMvc.perform(options("/api/brokers/me")
						.header(HttpHeaders.ORIGIN, CONSOLE_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONSOLE_ORIGIN));
	}

	@Test
	void 허용하지_않은_주소의_CORS_요청은_거부한다() throws Exception {
		mockMvc.perform(options("/api/brokers/me")
						.header(HttpHeaders.ORIGIN, "https://evil.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

	private ResultActions postBroker(String body) throws Exception {
		return mockMvc.perform(post("/api/brokers")
				.header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}
}
