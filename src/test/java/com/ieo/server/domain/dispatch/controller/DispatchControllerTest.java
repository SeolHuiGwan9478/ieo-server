package com.ieo.server.domain.dispatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.repository.TripPositionRepository;
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
import java.time.temporal.ChronoUnit;
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
	private BrokerDriverLinkRepository linkRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private TripPositionRepository positionRepository;

	@Autowired
	private com.ieo.server.domain.dispatch.repository.OrderRepository orderRepository;

	private MockMvc mockMvc;
	private String broker;
	private BrokerMember hanbitMember;
	private String driver;
	private Long driverId;
	private Site pyeongtaek;
	private Site daegu;
	private int phoneSeq = 10;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		Broker hanbit = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		broker = brokerBearer(hanbit, BrokerMemberPosition.STAFF);
		hanbitMember = memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(hanbit.getId()).get(0);
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
				.andExpect(jsonPath("$.cargoCategory").value("GENERAL"))
				.andExpect(jsonPath("$.cargoCategoryLabel").value("일반"))
				.andExpect(jsonPath("$.cargoType").value("음료 파레트"))
				.andExpect(jsonPath("$.pendingRequestCount").value(0))
				.andExpect(jsonPath("$.fare").value(450000))
				.andExpect(jsonPath("$.billing.customerName").value("대한제강"))
				.andExpect(jsonPath("$.billing.billedFare").value(600000))
				.andExpect(jsonPath("$.billing.fee").value(150000))
				.andExpect(jsonPath("$.billing.feeRate").value(25.0));

		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"cargoType\": \"음료 파레트\",", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400"));
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"cargoCategory\": \"GENERAL\", ", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400"));
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"GENERAL\"", "\"COLD\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400"));
		mockMvc.perform(post("/api/broker/orders").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content(orderJson("TON_5", "WING", "SELECT", null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ORDER_403_ROLE"));
	}

	@Test
	void 청구운임은_수수료율로_계산할_수_있고_비우면_수수료가_없다() throws Exception {
		// 지급 450,000원, 수수료율 25% → 청구 600,000원 (수수료 150,000원 = 청구의 25%)
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"billedFare\": 600000,", "\"feeRate\": 25,"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.billing.billedFare").value(600000))
				.andExpect(jsonPath("$.billing.feeRate").value(25.0));
		// 12.5% → 514,285.7원 → 1원 단위 반올림 514,286원
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"billedFare\": 600000,", "\"feeRate\": 12.5,"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.billing.billedFare").value(514286));
		// 둘 다 비우면 거래처만 남고 수수료는 없다
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"billedFare\": 600000,", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.billing.customerName").value("대한제강"))
				.andExpect(jsonPath("$.billing.billedFare").doesNotExist())
				.andExpect(jsonPath("$.billing.fee").doesNotExist());
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"billedFare\": 600000,", "\"feeRate\": 100,"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BROKER_400_FEE_RATE"));
		registerOrder(orderJson("TON_5", "WING", "SELECT", null).replace("\"billedFare\": 600000,", "\"billedFare\": -1,"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400"));
	}

	@Test
	void 차주는_내_차로_맡을_수_있는_오더만_가까운_곳에서_본다() throws Exception {
		Integer fits = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));   // 11톤 윙으로 가능
		registerOrder(orderJson("TON_25", "WING", "SELECT", null));                        // 톤수 부족
		registerOrder(orderJson("TON_5", "CARGO", "SELECT", null));                        // 차종 다름
		registerOrder(orderJson("TON_5", "WING", "SELECT", linkedDriver(Tonnage.TON_5, BodyType.WING))); // 남에게 지정

		mockMvc.perform(get("/api/orders").header("Authorization", driver)
						.param("lat", String.valueOf(LAT + 0.1)).param("lng", String.valueOf(LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(fits))
				// 직선 11,119m × 도로 계수 1.3. 상세의 실제 경로와 크게 다르지 않게 도로 거리 어림을 준다
				.andExpect(jsonPath("$[0].distanceM").value(14455))
				.andExpect(jsonPath("$[0].netProfit").isNumber())
				.andExpect(jsonPath("$[0].myRequest").doesNotExist())
				// 끝난 운행이 없는 새 회사라 신뢰 지표는 아직 공개 전 (BRK-032)
				.andExpect(jsonPath("$[0].brokerMetrics.published").value(false))
				.andExpect(jsonPath("$[0].brokerMetrics.sampleCount").value(0))
				.andExpect(jsonPath("$[0].brokerMetrics.minSampleCount").value(20))
				.andExpect(jsonPath("$[0].brokerMetrics.avgPaymentDays").doesNotExist());

		mockMvc.perform(get("/api/orders/" + fits + "/profit").header("Authorization", driver)
						.param("lat", String.valueOf(LAT + 0.1)).param("lng", String.valueOf(LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.netProfit").isNumber())
				.andExpect(jsonPath("$.fuelCost").isNumber());

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
				.andExpect(jsonPath("$.myRequest.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.fare").value(450000))
				.andExpect(jsonPath("$.billing").doesNotExist()); // 청구운임·거래처는 차주에게 안 보인다
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

		Integer targeted = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", linkedDriver(Tonnage.TON_5, BodyType.WING))));
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

		Broker rival = brokerRepository.save(Broker.create("경쟁물류", "9876543215", "제2026-002호", null));
		String rivalBearer = brokerBearer(rival, BrokerMemberPosition.OWNER);
		mockMvc.perform(get("/api/broker/orders/" + orderId).header("Authorization", rivalBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_404"));
		mockMvc.perform(get("/api/broker/orders").header("Authorization", rivalBearer))
				.andExpect(jsonPath("$.totalCount").value(0));
	}

	@Test
	void 차주가_배차를_취소하면_사유가_남고_오더는_다시_모집되어_다른_차주가_맡는다() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "FIRST", null)));
		Integer tripId = JsonPath.read(request(driver, orderId).andReturn().getResponse().getContentAsString(), "$.tripId");

		mockMvc.perform(post("/api/trips/" + tripId + "/cancel").header("Authorization", driver))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/trips/" + tripId + "/cancel").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"VEHICLE_PROBLEM\",\"memo\":\"타이어 펑크\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$[0].status").value("CANCELED"));
		mockMvc.perform(get("/api/broker/orders/" + orderId).header("Authorization", broker))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.open").value(true));

		String other = bearer(newDriver(Tonnage.TON_5, BodyType.WING));
		request(other, orderId)
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.tripId").value(org.hamcrest.Matchers.not(tripId)));
	}

	@Test
	void 주선사가_배차를_취소하고_오더를_내릴_수_있다() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		cancelByBroker(orderId, "{\"reason\":\"SHIPPER_CANCELED\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_409_NOT_ASSIGNED"));

		Integer requestId = idOf(request(driver, orderId));
		mockMvc.perform(post("/api/broker/orders/" + orderId + "/requests/" + requestId + "/confirm").header("Authorization", broker));
		String requests = mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andReturn().getResponse().getContentAsString();
		Integer tripId = JsonPath.read(requests, "$[0].tripId");
		// 출발해서 위치를 보낸 뒤 주선사가 취소하면 위치도 지워진다 (TRIP-011)
		mockMvc.perform(post("/api/trips/" + tripId + "/start").header("Authorization", driver));
		mockMvc.perform(post("/api/trips/" + tripId + "/locations").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"points\":[{\"recordedAt\":\"" + Instant.now().minusSeconds(60)
								+ "\",\"lat\":36.5,\"lng\":127.0,\"remainMeters\":200000,\"remainSeconds\":9000}]}"))
				.andExpect(status().isNoContent());
		assertThat(positionRepository.findById(tripId.longValue())).isPresent();

		cancelByBroker(orderId, "{\"reason\":\"SHIPPER_CANCELED\",\"reopen\":false}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$[0].status").value("CANCELED"));
		mockMvc.perform(get("/api/trips/" + tripId).header("Authorization", driver))
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(positionRepository.findById(tripId.longValue())).isEmpty();
	}

	@Test
	void 상차_2시간_이내_취소는_차주_이행_기록에_남는다() throws Exception {
		Integer soon = idOf(registerOrder(orderJson("TON_5", "WING", "FIRST", null, Instant.now().plus(Duration.ofHours(1)))));
		Integer tripId = JsonPath.read(request(driver, soon).andReturn().getResponse().getContentAsString(), "$.tripId");
		mockMvc.perform(post("/api/trips/" + tripId + "/cancel").header("Authorization", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SCHEDULE_CONFLICT\"}"));

		Integer next = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		request(driver, next);
		mockMvc.perform(get("/api/broker/orders/" + next + "/requests").header("Authorization", broker))
				.andExpect(jsonPath("$[0].lateCancelCount").value(1))
				.andExpect(jsonPath("$[0].completedTripCount").value(0));
	}

	@Test
	void 오더는_한_번만_신고할_수_있다() throws Exception {
		Integer orderId = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));

		report(orderId, "{\"type\":\"OVERLOAD\",\"memo\":\"5톤 오더인데 7톤 실으라고 함\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.typeLabel").value("과적 요구"));
		report(orderId, "{\"type\":\"FALSE_INFO\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_409_REPORTED"));
		report(orderId, "{}").andExpect(status().isBadRequest());
	}

	private ResultActions cancelByBroker(Integer orderId, String json) throws Exception {
		return mockMvc.perform(post("/api/broker/orders/" + orderId + "/cancel").header("Authorization", broker)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions report(Integer orderId, String json) throws Exception {
		return mockMvc.perform(post("/api/orders/" + orderId + "/reports").header("Authorization", driver)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void 단골이_아닌_차주에게는_지정_배차할_수_없고_마켓_공개는_누구나_요청한다() throws Exception {
		Long stranger = newDriver(Tonnage.TON_5, BodyType.WING);
		registerOrder(orderJson("TON_5", "WING", "SELECT", stranger))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400_TARGET"));
		registerOrder(orderJson("TON_5", "WING", "SELECT", 999999L))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400_TARGET"));

		Integer open = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		request(bearer(stranger), open).andExpect(status().isCreated());

		Long regular = linkedDriver(Tonnage.TON_5, BodyType.WING);
		Integer targeted = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", regular)));
		request(bearer(regular), targeted).andExpect(status().isCreated());
	}

	@Test
	void 단골을_해제하면_새_지정은_막히고_이미_올린_지정_오더는_그대로다() throws Exception {
		Long regular = linkedDriver(Tonnage.TON_5, BodyType.WING);
		Integer targeted = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", regular)));
		linkRepository.findAll().forEach(link -> link.unlink(Instant.now()));

		registerOrder(orderJson("TON_5", "WING", "SELECT", regular))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_400_TARGET"));
		request(bearer(regular), targeted).andExpect(status().isCreated());
	}

	@Test
	void 지급일을_비우면_회사_정산_주기로_채우고_넣으면_그_값을_쓴다() throws Exception {
		brokerRepository.findByBusinessNumber("1234567891").orElseThrow().changeSettlementDays(45);
		String withoutDays = orderJson("TON_5", "WING", "SELECT", null).replace("\"expectedPaymentDays\": 15,", "");

		registerOrder(withoutDays)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.expectedPaymentDays").value(45));
		registerOrder(orderJson("TON_5", "WING", "SELECT", null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.expectedPaymentDays").value(15));
	}

	@Test
	void 오더_수정은_모집_중이면_전부되고_요청이_기다리거나_확정된_뒤에는_거래처와_청구운임만_된다() throws Exception {
		Instant from = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
		String original = orderJson("TON_5", "WING", "SELECT", null, from);
		String raised = original.replace("\"fare\": 450000", "\"fare\": 480000");

		// 요청이 없으면 차주에게 보이는 조건도 바꿀 수 있다
		Integer quiet = idOf(registerOrder(original));
		updateOrder(quiet, broker, raised.replace("\"cargoType\": \"음료 파레트\"", "\"cargoType\": \"건축 자재\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.fare").value(480000))
				.andExpect(jsonPath("$.cargoType").value("건축 자재"))
				.andExpect(jsonPath("$.billing.billedFare").value(600000));

		// 대기 중 요청이 있으면 조건은 못 바꾸고(409) 청구운임·거래처만 된다. 요청은 그대로 기다린다
		Integer orderId = idOf(registerOrder(original));
		Integer requestId = idOf(request(driver, orderId));
		updateOrder(orderId, broker, raised)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_409_REQUESTS"));
		updateOrder(orderId, broker, original.replace("\"billedFare\": 600000", "\"billedFare\": 650000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.billing.billedFare").value(650000))
				.andExpect(jsonPath("$.billing.fee").value(200000))
				.andExpect(jsonPath("$.pendingRequestCount").value(1));
		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$[0].status").value("PENDING"));

		// 확정 뒤에도 거래처·청구운임만. 조건을 바꾸면 409
		mockMvc.perform(post("/api/broker/orders/" + orderId + "/requests/" + requestId + "/confirm").header("Authorization", broker))
				.andExpect(status().isOk());
		updateOrder(orderId, broker, raised)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_409_LOCKED"));
		updateOrder(orderId, broker, original.replace("\"customerName\": \"대한제강\"", "\"customerName\": \"성원건설\"")
						.replace("\"billedFare\": 600000", "\"billedFare\": 700000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.fare").value(450000))
				.andExpect(jsonPath("$.billing.customerName").value("성원건설"))
				.andExpect(jsonPath("$.billing.billedFare").value(700000));

		// 다른 회사는 404, 내린 오더는 409
		Broker rival = brokerRepository.save(Broker.create("경쟁물류", "9876543215", "제2026-002호", null));
		updateOrder(orderId, brokerBearer(rival, BrokerMemberPosition.OWNER), original)
				.andExpect(status().isNotFound());
		Integer closedId = idOf(registerOrder(original));
		mockMvc.perform(post("/api/broker/orders/" + closedId + "/close").header("Authorization", broker))
				.andExpect(status().isOk());
		updateOrder(closedId, broker, original)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_409_CLOSED"));
	}

	@Test
	void 목록은_상차_날짜_최소_운임_결제_방식_하차_지역으로_거른다() throws Exception {
		Instant tomorrow = Instant.now().plus(Duration.ofDays(1));
		Integer cheap = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null, tomorrow).replace("\"fare\": 450000", "\"fare\": 200000")));
		Integer prepaid = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null, tomorrow).replace("\"RECEIPT\"", "\"PREPAID\"")));
		Integer later = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null, tomorrow.plus(Duration.ofDays(2)))));
		String day = tomorrow.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate().toString();

		mockMvc.perform(get("/api/orders").header("Authorization", driver).param("pickupDate", day))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(later))))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItems(cheap, prepaid)));
		mockMvc.perform(get("/api/orders").header("Authorization", driver).param("minFare", "300000"))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(cheap))));
		mockMvc.perform(get("/api/orders").header("Authorization", driver).param("paymentType", "PREPAID"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(prepaid));
		mockMvc.perform(get("/api/orders").header("Authorization", driver).param("dropoffArea", "대구"))
				.andExpect(jsonPath("$.length()").value(3));
		mockMvc.perform(get("/api/orders").header("Authorization", driver).param("dropoffArea", "부산"))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 반경은_화면에_보이는_도로_거리_어림으로_거른다() throws Exception {
		Integer order = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		// 평택에서 위로 0.17도 ≈ 직선 18.9km, 도로 어림 24.6km
		String lat = String.valueOf(LAT + 0.17);

		mockMvc.perform(get("/api/orders").header("Authorization", driver)
						.param("lat", lat).param("lng", String.valueOf(LNG)).param("radiusM", "20000"))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(order))));
		mockMvc.perform(get("/api/orders").header("Authorization", driver)
						.param("lat", lat).param("lng", String.valueOf(LNG)).param("radiusM", "25000"))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(order)))
				.andExpect(jsonPath("$[0].distanceM", org.hamcrest.Matchers.lessThanOrEqualTo(25000)));
	}

	@Test
	void 일분_안에_요청을_몰아_걸면_막는다() throws Exception {
		for (int i = 0; i < 5; i++) {
			Integer order = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
			Integer requestId = idOf(request(driver, order).andExpect(status().isCreated()));
			mockMvc.perform(post("/api/dispatch-requests/" + requestId + "/withdraw").header("Authorization", driver));
		}
		request(driver, idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null))))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("DISPATCH_429_FAST"));
	}

	@Test
	void 다른_회사가_같은_화물을_다시_올리면_재위탁_의심으로_표시한다() throws Exception {
		Integer original = idOf(registerOrder(orderJson("TON_5", "WING", "SELECT", null)));
		Broker other = brokerRepository.save(Broker.create("다른물류", "2234567890", "제2026-002호", null));
		String otherBroker = brokerBearer(other, BrokerMemberPosition.STAFF);
		Integer copied = idOf(mockMvc.perform(post("/api/broker/orders").header("Authorization", otherBroker)
				.contentType(MediaType.APPLICATION_JSON).content(orderJson("TON_5", "WING", "SELECT", null))));
		// 시간대가 겹치지 않으면 같은 노선이라도 다른 화물로 본다
		Integer otherDay = idOf(mockMvc.perform(post("/api/broker/orders").header("Authorization", otherBroker)
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderJson("TON_5", "WING", "SELECT", null, Instant.now().plus(Duration.ofDays(3))))));

		assertThat(orderRepository.findById(copied.longValue()).orElseThrow().getReconsignmentSuspectOf())
				.isEqualTo(original.longValue());
		assertThat(orderRepository.findById(otherDay.longValue()).orElseThrow().getReconsignmentSuspectOf()).isNull();
	}

	@Test
	void 주선사는_자주_쓰는_노선을_저장하고_불러와_지운다() throws Exception {
		String template = """
				{"name": "평택→대구 음료", "pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "cargoCategory": "GENERAL", "cargoType": "음료 파레트", "weightKg": 4500, "tonnage": "TON_5",
				 "bodyType": "WING", "fare": 450000, "paymentType": "RECEIPT"}
				""".formatted(pyeongtaek.getId());
		Integer id = idOf(mockMvc.perform(post("/api/broker/order-templates").header("Authorization", broker)
						.contentType(MediaType.APPLICATION_JSON).content(template))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pickup.id").value(pyeongtaek.getId()))
				.andExpect(jsonPath("$.dropoff.name").value("대구 성서 공단")));

		mockMvc.perform(get("/api/broker/order-templates").header("Authorization", broker))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("평택→대구 음료"))
				.andExpect(jsonPath("$[0].fare").value(450000));
		mockMvc.perform(post("/api/broker/order-templates").header("Authorization", broker)
						.contentType(MediaType.APPLICATION_JSON).content(template.replace("평택→대구 음료", " ")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/broker/order-templates").header("Authorization", driver))
				.andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/broker/order-templates/" + id).header("Authorization", broker))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/broker/order-templates").header("Authorization", broker))
				.andExpect(jsonPath("$.length()").value(0));
	}

	private ResultActions updateOrder(Integer orderId, String bearer, String json) throws Exception {
		return mockMvc.perform(put("/api/broker/orders/" + orderId).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String orderJson(String tonnage, String bodyType, String confirmMode, Long targetDriverId) {
		return orderJson(tonnage, bodyType, confirmMode, targetDriverId, Instant.now().plus(Duration.ofDays(1)));
	}

	private String orderJson(String tonnage, String bodyType, String confirmMode, Long targetDriverId, Instant pickupFrom) {
		return """
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "pickupFrom": "%s", "pickupUntil": "%s", "dropoffBy": "%s",
				 "cargoCategory": "GENERAL", "cargoType": "음료 파레트",
				 "weightKg": 4500, "tonnage": "%s", "bodyType": "%s", "loadingMethod": "FORKLIFT",
				 "fare": 450000, "billedFare": 600000, "customerName": "대한제강", "paymentType": "RECEIPT", "expectedPaymentDays": 15,
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

	/** 우리 회사 단골로 연결된 차주. 지정 배차는 단골에게만 할 수 있다 (BRK-002) */
	private Long linkedDriver(Tonnage tonnage, BodyType bodyType) {
		Long driverUserId = newDriver(tonnage, bodyType);
		Driver created = driverRepository.findById(driverUserId).orElseThrow();
		BrokerDriverLink link = BrokerDriverLink.request(hanbitMember, created.getUser().getPhone());
		link.accept(created, Instant.now());
		linkRepository.save(link);
		return driverUserId;
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
