package com.ieo.server.domain.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import com.ieo.server.domain.recommendation.service.TruckRouteEstimator;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class RecommendationControllerTest {

	@MockitoBean
	private TruckRouteEstimator truckRoutes;

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
	private TripRepository tripRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private String broker;
	private String driver;
	private Trip trip;
	private Instant now;
	/** 지금 운행의 하차지 */
	private Site seongseo;
	/** 성서에서 약 13km */
	private Site chilgok;
	/** 성서에서 약 10km */
	private Site bukgu;
	private Site busan;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		now = clock.instant();
		Broker hanbit = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		User brokerUser = userRepository.save(User.create("01090001601", UserRole.BROKER));
		memberRepository.save(BrokerMember.of(brokerUser, hanbit, BrokerMemberPosition.STAFF));
		broker = bearer(brokerUser.getId());

		User driverUser = userRepository.save(User.create("01090001602", UserRole.DRIVER));
		Driver created = driverRepository.save(Driver.of(driverUser));
		Vehicle vehicle = Vehicle.create(created, "경기82사1602", Tonnage.TON_11, BodyType.WING, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		driver = bearer(driverUser.getId());

		Site pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		seongseo = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		chilgok = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", 35.955, 128.528, null));
		bukgu = siteRepository.save(Site.create("대구 북구 창고", "대구 북구", 35.90, 128.58, null));
		busan = siteRepository.save(Site.create("부산 신항", "부산 강서구", 35.08, 128.83, null));

		trip = tripRepository.save(Trip.create(created, vehicle, null, "한빛로지스", 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, pyeongtaek, now, seongseo, now.plusSeconds(3_600)));
		trip.start(now);
		trip.arriveAtPickup();
		trip.departPickup();
	}

	@Test
	void 하차지_근처에서_내_차에_실리고_시간이_맞는_오더를_실수익_순으로_준다() throws Exception {
		Integer chilgokOrder = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer bukguOrder = order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(busan, 900_000, "TON_5", "WING", now.plus(Duration.ofHours(3)));             // 30km 밖
		order(chilgok, 500_000, "TON_25", "WING", now.plus(Duration.ofHours(2)));           // 톤수 부족
		order(chilgok, 500_000, "TON_5", "CARGO", now.plus(Duration.ofHours(2)));           // 차종 다름
		order(bukgu, 500_000, "TON_5", "WING", now.minus(Duration.ofHours(1)));             // 하차 끝나기 전에 상차 마감

		String body = recommend()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.freeAt").exists())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].order.id").value(chilgokOrder))
				.andExpect(jsonPath("$.items[1].order.id").value(bukguOrder))
				.andExpect(jsonPath("$.items[0].order.pickup.name").value("칠곡 물류센터"))
				.andExpect(jsonPath("$.items[0].longWait").value(false))
				.andReturn().getResponse().getContentAsString();

		// 실수익 = 운임 − (공차 + 운송) 기름값. 경로를 못 받으면 통행료는 비운다
		long emptyMeters = ((Number) JsonPath.read(body, "$.items[0].emptyMeters")).longValue();
		int fuelCost = JsonPath.read(body, "$.items[0].fuelCost");
		int netProfit = JsonPath.read(body, "$.items[0].netProfit");
		assertThat(emptyMeters).isBetween(16_000L, 18_000L);   // 직선 약 13km × 1.3
		assertThat(fuelCost).isGreaterThan((int) Math.round(emptyMeters / 1000.0 / 3.5 * 1600));
		assertThat(netProfit).isEqualTo(300_000 - fuelCost);
		assertThat((Object) JsonPath.read(body, "$.items[0].tollFare")).isNull();
		assertThat(((Number) JsonPath.read(body, "$.items[0].order.distanceM")).longValue()).isEqualTo(emptyMeters);
	}

	@Test
	void 실제_경로를_받으면_두_구간의_기름값과_통행료를_뺀다() throws Exception {
		order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		given(truckRoutes.routes(any(), any())).willAnswer(call -> {
			Map<TruckRouteEstimator.Leg, TruckRouteEstimator.TruckRoute> found = new HashMap<>();
			Collection<TruckRouteEstimator.Leg> legs = call.getArgument(0);
			legs.forEach(leg -> found.put(leg, new TruckRouteEstimator.TruckRoute(35_000, 1_800, 4_000)));
			return found;
		});

		String body = recommend().andExpect(jsonPath("$.items[0].tollFare").value(8_000))
				.andExpect(jsonPath("$.items[0].emptyMeters").value(35_000))
				.andReturn().getResponse().getContentAsString();

		int fuelCost = JsonPath.read(body, "$.items[0].fuelCost");
		assertThat(fuelCost).isEqualTo((int) Math.round(70.0 / 3.5 * 1600));
		assertThat((int) JsonPath.read(body, "$.items[0].netProfit")).isEqualTo(300_000 - fuelCost - 8_000);
	}

	@Test
	void 안내한_오더는_다시_추천하지_않고_수락하면_배차_요청이_간다() throws Exception {
		Integer first = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer second = order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));

		answer(first, "NEXT").andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("NEXT"));
		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.id").value(second));

		answer(second, "ACCEPTED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dispatchRequest.status").value("PENDING"));
		mockMvc.perform(get("/api/dispatch-requests").header("Authorization", driver))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].orderId").value(second));
		recommend().andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void 하차지에_도착하면_안내한_오더도_다시_보여_주고_거절한_것만_뺀다() throws Exception {
		Integer heard = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer rejected = order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer other = order(bukgu, 270_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));

		answer(heard, null);
		answer(rejected, "REJECTED");
		recommend().andExpect(jsonPath("$.items.length()").value(1));

		trip.arriveAtDropoff();
		recommend().andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].order.id").value(heard))
				.andExpect(jsonPath("$.items[1].order.id").value(other));
	}

	@Test
	void 한_운행에서_3번_거절하면_더_추천하지_않는다() throws Exception {
		List<Integer> orders = List.of(
				order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2))),
				order(chilgok, 290_000, "TON_5", "WING", now.plus(Duration.ofHours(2))),
				order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2))),
				order(bukgu, 270_000, "TON_5", "WING", now.plus(Duration.ofHours(2))));

		answer(orders.get(0), "REJECTED");
		answer(orders.get(1), "REJECTED");
		recommend().andExpect(jsonPath("$.items.length()").value(2));
		answer(orders.get(2), "REJECTED");
		recommend().andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.freeAt").doesNotExist());
	}

	@Test
	void 추천을_끄거나_반경을_줄이면_그만큼만_추천한다() throws Exception {
		order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));

		mockMvc.perform(get("/api/recommendation-preference").header("Authorization", driver))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.radiusKm").value(30));

		preference("{\"enabled\": true, \"radiusKm\": 11}").andExpect(status().isOk());
		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.pickup.name").value("대구 북구 창고"));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"minNetProfit\": 220000}");
		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.pickup.name").value("칠곡 물류센터"));

		preference("{\"enabled\": false, \"radiusKm\": 30}");
		recommend().andExpect(jsonPath("$.items.length()").value(0));

		preference("{\"enabled\": true, \"radiusKm\": 3}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 제외한_화물_종류는_추천하지_않는다() throws Exception {
		order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)), "MOVING");
		order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)), "GENERAL");

		mockMvc.perform(get("/api/recommendation-preference").header("Authorization", driver))
				.andExpect(jsonPath("$.excludedCargoCategories.length()").value(0));
		recommend().andExpect(jsonPath("$.items.length()").value(2));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"excludedCargoCategories\": [\"MOVING\", \"COLD\"]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludedCargoCategories[0]").value("COLD"))
				.andExpect(jsonPath("$.excludedCargoCategories[1]").value("MOVING"));
		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.pickup.name").value("대구 북구 창고"));

		// 제외 화물을 보내지 않으면 그대로 둔다
		preference("{\"enabled\": true, \"radiusKm\": 30}")
				.andExpect(jsonPath("$.excludedCargoCategories.length()").value(2));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"excludedCargoCategories\": []}");
		recommend().andExpect(jsonPath("$.items.length()").value(2));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"excludedCargoCategories\": [\"FROZEN\"]}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 운송을_시작하기_전에는_추천하지_않는다() throws Exception {
		order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Trip scheduled = tripRepository.save(Trip.create(trip.getDriver(), trip.getVehicle(), null, "한빛로지스",
				300_000, PaymentType.RECEIPT, TripSource.MANUAL, chilgok, now, seongseo, now.plusSeconds(3_600)));

		mockMvc.perform(get("/api/trips/" + scheduled.getId() + "/recommendations").header("Authorization", driver))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(put("/api/trips/" + scheduled.getId() + "/recommendations/1").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content("{\"answer\": \"NEXT\"}"))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/api/trips/" + trip.getId() + "/recommendations").header("Authorization", broker))
				.andExpect(status().isForbidden());
	}

	private ResultActions recommend() throws Exception {
		return mockMvc.perform(get("/api/trips/" + trip.getId() + "/recommendations").header("Authorization", driver));
	}

	private ResultActions answer(Integer orderId, String answer) throws Exception {
		return mockMvc.perform(put("/api/trips/" + trip.getId() + "/recommendations/" + orderId)
				.header("Authorization", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{\"answer\": " + (answer == null ? "null" : "\"" + answer + "\"") + "}"));
	}

	private ResultActions preference(String json) throws Exception {
		return mockMvc.perform(put("/api/recommendation-preference").header("Authorization", driver)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** 상차 가능 시간은 pickupFrom부터 2시간 */
	private Integer order(Site pickup, int fare, String tonnage, String bodyType, Instant pickupFrom) throws Exception {
		return order(pickup, fare, tonnage, bodyType, pickupFrom, "GENERAL");
	}

	private Integer order(Site pickup, int fare, String tonnage, String bodyType, Instant pickupFrom,
			String cargoCategory) throws Exception {
		String json = """
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "부산 신항", "address": "부산 강서구", "lat": 35.08, "lng": 128.83}},
				 "pickupFrom": "%s", "pickupUntil": "%s", "dropoffBy": "%s",
				 "cargoCategory": "%s", "cargoType": "음료 파레트",
				 "weightKg": 4500, "tonnage": "%s", "bodyType": "%s", "loadingMethod": "FORKLIFT",
				 "fare": %d, "paymentType": "RECEIPT", "expectedPaymentDays": 15,
				 "consignmentType": "DIRECT", "confirmMode": "SELECT", "targetDriverId": null}
				""".formatted(pickup.getId(), pickupFrom, pickupFrom.plus(Duration.ofHours(2)),
				pickupFrom.plus(Duration.ofHours(8)), cargoCategory, tonnage, bodyType, fare);
		String body = mockMvc.perform(post("/api/broker/orders").header("Authorization", broker)
						.contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}
}
