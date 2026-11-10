package com.ieo.server.domain.dispatch.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.repository.DriverDocumentRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 서류 심사 게이트: 필수 서류 4종이 모두 승인된 차주만 배차를 요청한다 (ieo.review.gate-driver-requests) */
@SpringBootTest(properties = "ieo.review.gate-driver-requests=true")
@ActiveProfiles("test")
@Transactional
class DriverRequestGateTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Autowired
	private DriverDocumentRepository documentRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String broker;
	private Driver driver;
	private String driverBearer;
	private Site pyeongtaek;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		Broker hanbit = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		User brokerUser = userRepository.save(User.create("01090003001", UserRole.BROKER));
		memberRepository.save(BrokerMember.of(brokerUser, hanbit, BrokerMemberPosition.STAFF));
		broker = bearer(brokerUser.getId());

		User driverUser = userRepository.save(User.create("01090003002", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(driverUser));
		Vehicle vehicle = Vehicle.create(driver, "경기82사3002", Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		driverBearer = bearer(driverUser.getId());
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시 포승읍", 36.98, 126.84, null));
	}

	@Test
	void 필수_서류가_모두_승인되기_전에는_오더는_보이지만_배차_요청은_막힌다() throws Exception {
		Integer orderId = JsonPath.read(registerOrder().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		// 보기는 되지만 줄인 모양: 상하차지는 시·구까지, 주선사·화물 내용은 비운다
		mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", driverBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.limited").value(true))
				.andExpect(jsonPath("$.brokerName").isEmpty())
				.andExpect(jsonPath("$.pickup.address").value("경기 평택시"))
				.andExpect(jsonPath("$.pickup.id").isEmpty())
				.andExpect(jsonPath("$.cargoType").value("일반"))
				.andExpect(jsonPath("$.fare").value(450000));
		mockMvc.perform(get("/api/orders").header("Authorization", driverBearer))
				.andExpect(jsonPath("$[0].limited").value(true))
				.andExpect(jsonPath("$[0].dropoff.address").value("대구 달서구"));

		// 인증 마크와 이력: 아직 인증 전
		mockMvc.perform(get("/api/drivers/me/record").header("Authorization", driverBearer))
				.andExpect(jsonPath("$.verified").value(false))
				.andExpect(jsonPath("$.completedTripCount").value(0))
				.andExpect(jsonPath("$.lateCancelCount").value(0));

		// 3종만 승인: 아직 막힌다
		approve(DocumentType.VEHICLE_REGISTRATION);
		approve(DocumentType.DRIVER_LICENSE);
		approve(DocumentType.FREIGHT_QUALIFICATION);
		request(orderId)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DRIVER_403_UNVERIFIED"));

		// 4종 모두 승인되면 요청할 수 있다
		approve(DocumentType.BUSINESS_REGISTRATION);
		request(orderId).andExpect(status().isCreated());
		mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", driverBearer))
				.andExpect(jsonPath("$.limited").value(false))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$.pickup.address").value("경기 평택시 포승읍"));
		mockMvc.perform(get("/api/drivers/me/record").header("Authorization", driverBearer))
				.andExpect(jsonPath("$.verified").value(true));
		// 주선사는 요청한 차주 목록에서 인증 마크와 이력을 본다
		mockMvc.perform(get("/api/broker/orders/" + orderId + "/requests").header("Authorization", broker))
				.andExpect(jsonPath("$[0].verified").value(true))
				.andExpect(jsonPath("$[0].completedTripCount").value(0));
	}

	private void approve(DocumentType type) {
		BusinessCheck check = type == DocumentType.BUSINESS_REGISTRATION
				? BusinessCheck.of("123-45-67800", LocalDate.of(2020, 1, 31), "김기사")
				: null;
		DriverDocument document = DriverDocument.submit(driver, type, "documents/test-" + type, "image/png", check);
		document.approve(1L, Instant.now());
		documentRepository.save(document);
	}

	private ResultActions registerOrder() throws Exception {
		Instant pickupFrom = Instant.now().plus(Duration.ofDays(1));
		String json = """
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "pickupFrom": "%s", "pickupUntil": "%s", "dropoffBy": "%s",
				 "cargoCategory": "GENERAL", "cargoType": "음료 파레트",
				 "weightKg": 4500, "tonnage": "TON_5", "bodyType": "CARGO", "loadingMethod": "FORKLIFT",
				 "fare": 450000, "paymentType": "RECEIPT", "expectedPaymentDays": 15,
				 "consignmentType": "DIRECT", "confirmMode": "SELECT"}
				""".formatted(pyeongtaek.getId(), pickupFrom, pickupFrom.plus(Duration.ofHours(2)), pickupFrom.plus(Duration.ofHours(8)));
		return mockMvc.perform(post("/api/broker/orders").header("Authorization", broker)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions request(Integer orderId) throws Exception {
		return mockMvc.perform(post("/api/orders/" + orderId + "/requests").header("Authorization", driverBearer));
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}
}
