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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DispatchControllerTest {

	/** 평택 포승 */
	private static final double LAT = 36.98;
	private static final double LNG = 126.84;

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
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String broker;
	private String driver;
	private Long driverId;
	private Site pyeongtaek;
	private Site daegu;
	private int phoneSeq = 10;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		Broker hanbit = brokerRepository.save(Broker.create("한빛로지스", "1234567890", "제2026-001호", null));
		broker = brokerBearer(hanbit, BrokerMemberPosition.STAFF);
		driverId = newDriver(Tonnage.TON_11, BodyType.WING);
		driver = bearer(driverId);
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시 포승읍", LAT, LNG, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 주선사가_필수_항목을_갖춰_오더를_등록한다() throws Exception {
		registerOrder(orderJson("TON_5", "WING", "SELECT", null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.open").value(true))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$.pickup.name").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.dropoff.name").value("대구 성서 공단"))
				.andExpect(jsonPath("$.tonnageLabel").value("5톤"))
				.andExpect(jsonPath("$.bodyTypeLabel").exists())
				.andExpect(jsonPath("$.loadingMethodLabel").value("지게차"))
				.andExpect(jsonPath("$.pendingRequestCount").value(0));

		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"cargoType\": \"음료 파레트\",", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400"));
		mockMvc.perform(post("/api/broker/orders").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content(orderJson("TON_5", "WING", "SELECT", null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ORDER_403_ROLE"));
	}

	@Test
	void 차주는_내_차로_맡을_수_있는_오더만_가까운_곳에서_본다() throws Exception {
		Integer fits = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));   // 11톤 윙으로 가능
		registerOrder(orderJson("TON_25", "WING", "SELECT", null));                        // 톤수 부족
		registerOrder(orderJson("TON_5", "CARGO", "SELECT", null));                        // 차종 다름
		registerOrder(orderJson("TON_5", "WING", "SELECT", newDriver(Tonnage.TON_5, BodyType.WING))); // 남에게 지정

		mockMvc.perform(get("/api/orders").header("Authorization", driver)
						.param("lat", String.valueOf(LAT + 0.1)).param("lng", String.valueOf(LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(fits))
				.andExpect(jsonPath("$[0].distanceM").value(11119))
				.andExpect(jsonPath("$[0].myRequest").doesNotExist());

		mockMvc.perform(get("/api/orders").header("Authorization", driver)
						.param("lat", "35.1").param("lng", "129.0").param("radiusM", "50000"))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 요청하고_주선사가_확정하면_운행이_생기고_나머지_요청은_마감된다() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		String other = bearer(newDriver(Tonnage.TON_5, BodyType.WING));

		Integer mine = idOf(request(driver, orderId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.expiresAt").exists()));
		Integer theirs = idOf(request(other, orderId));
		request(driver, orderId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DISPATCH_409_DUPLICATE"));

		mockMvc.perform(get("/api/broker/orders").header("Authorization", broker))
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.items[0].pendingRequestCount").value(2));
		mockMvc.perform(get("/api/broker/orders/" + orderId + "/requests").header("Authorization", broker))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].requestId").value(mine))
				.andExpect(jsonPath("$[0].tonnageLabel").value("11톤"))
				.andExpect(jsonPath("$[0].completedTripCount").value(0));

		mockMvc.perform(post("/api/broker/orders/" + orderId + "/requests/" + mine + "/confirm").header("Authorization", broker))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.open").value(false));

		String myRequests = mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$[0].tripId").exists())
				.andReturn().getResponse().getContentAsString();
		Integer tripId = JsonPath.read(myRequests, "$[0].tripId");
		mockMvc.perform(get("/api/trips/" + tripId).header("Authorization", driver))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.source").value("IEO_DISPATCH"))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$.fare").value(450000))
				.andExpect(jsonPath("$.pickup.siteId").value(pyeongtaek.getId()));
		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", other))
				.andExpect(jsonPath("$[0].id").value(theirs))
				.andExpect(jsonPath("$[0].status").value("DECLINED"));

		mockMvc.perform(post("/api/broker/orders/" + orderId + "/requests/" + theirs + "/confirm").header("Authorization", broker))
				.andExpect(status().isConflict());
	}

	@Test
	void 자동_확정_오더는_요청하자마자_운행이_생긴다() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "FIRST", null)));

		request(driver, orderId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.tripId").exists());
		mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", driver))
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.myRequest.status").value("CONFIRMED"));
	}

	@Test
	void 대기_중_요청은_3건까지이고_철회하면_다시_걸_수_있다() throws Exception {
		Integer first = idOf(request(driver, idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)))));
		request(driver, idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null))));
		request(driver, idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null))));
		Integer fourth = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));

		request(driver, fourth)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("DISPATCH_429_LIMIT"));
		mockMvc.perform(post("/api/dispatch-requests/" + first + "/withdraw").header("Authorization", driver))
				.andExpect(jsonPath("$.status").value("WITHDRAWN"));
		request(driver, fourth).andExpect(status().isCreated());
	}

	@Test
	void 차가_맞지_않거나_남에게_지정된_오더는_요청할_수_없다() throws Exception {
		Integer cargo = idOf(registerOrder(orderJson("TON_5", "CARGO", "SELECT", null)));
		request(driver, cargo)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DISPATCH_400_VEHICLE"));

		Integer targeted = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", newDriver(Tonnage.TON_5, BodyType.WING))));
		request(driver, targeted).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orders/" + targeted).header("Authorization", driver))
				.andExpect(status().isNotFound());
	}

	@Test
	void 오더를_내리면_대기_요청이_마감되고_다른_회사_오더는_404() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		request(driver, orderId);

		mockMvc.perform(post("/api/broker/orders/" + orderId + "/close").header("Authorization", broker))
				.andExpect(jsonPath("$.status").value("CLOSED"));
		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$[0].status").value("DECLINED"));

		Broker rival = brokerRepository.save(Broker.create("경쟁물류", "9876543210", "제2026-002호", null));
		String rivalBearer = brokerBearer(rival, BrokerMemberPosition.OWNER);
		mockMvc.perform(get("/api/broker/orders/" + orderId).header("Authorization", rivalBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_404"));
		mockMvc.perform(get("/api/broker/orders").header("Authorization", rivalBearer))
				.andExpect(jsonPath("$.totalCount").value(0));
	}

	private String orderJson(String tonnage, String bodyType, String confirmMode, Long targetDriverId) {
		Instant pickupFrom = Instant.now().plus(Duration.ofDays(1));
		return """
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "pickupFrom": "%s", "pickupUntil": "%s", "dropoffBy": "%s",
				 "cargoType": "음료 파레트",
				 "weightKg": 4500, "tonnage": "%s", "bodyType": "%s", "loadingMethod": "FORKLIFT",
				 "fare": 450000, "paymentType": "RECEIPT", "expectedPaymentDays": 15,
				 "consignmentType": "DIRECT", "confirmMode": "%s", "targetDriverId": %s}
				""".formatted(pyeongtaek.getId(), pickupFrom, pickupFrom.plus(Duration.ofHours(2)),
				pickupFrom.plus(Duration.ofHours(8)), tonnage, bodyType, confirmMode, targetDriverId);
	}

	private ResultActions registerOrder(String json) throws Exception {
		return mockMvc.perform(post("/api/broker/orders").header("Authorization", broker)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions request(String bearer, Integer orderId) throws Exception {
		return mockMvc.perform(post("/api/orders/" + orderId + "/requests").header("Authorization", bearer));
	}

	private Long newDriver(Tonnage tonnage, BodyType bodyType) {
		User user = userRepository.save(User.create(nextPhone(), UserRole.DRIVER));
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + (1000 + phoneSeq), tonnage, bodyType, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return user.getId();
	}

	private String brokerBearer(Broker company, BrokerMemberPosition position) {
		User user = userRepository.save(User.create(nextPhone(), UserRole.BROKER));
		memberRepository.save(BrokerMember.of(user, company, position));
		return bearer(user.getId());
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	private String nextPhone() {
		return "010900014" + String.format("%02d", phoneSeq++);
	}

	private Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
