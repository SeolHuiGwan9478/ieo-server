package com.ieo.server.domain.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.ieo.server.domain.recommendation.repository.RecommendationShownRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripRepository;
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
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
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
	private DispatchRequestRepository requestRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private RecommendationShownRepository shownRepository;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private String broker;
	private String driver;
	private Driver driverEntity;
	private Vehicle vehicleEntity;
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
		vehicleEntity = vehicleRepository.save(vehicle);
		driverEntity = created;
		driver = bearer(driverUser.getId());

		seongseo = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		chilgok = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", 35.955, 128.528, null));
		bukgu = siteRepository.save(Site.create("대구 북구 창고", "대구 북구", 35.90, 128.58, null));
		busan = siteRepository.save(Site.create("부산 신항", "부산 강서구", 35.08, 128.83, null));

	}

	@Test
	void 운행이_없으면_세운_자리_근처에서_내_차에_실리는_오더_중_실수익_1등_1건만_준다() throws Exception {
		Integer first = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(chilgok, 290_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(bukgu, 270_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));               // 4등이라 경로도 재지 않는다
		order(busan, 900_000, "TON_5", "WING", now.plus(Duration.ofHours(3)));               // 30km 밖
		order(chilgok, 500_000, "TON_25", "WING", now.plus(Duration.ofHours(2)));            // 톤수 부족
		order(chilgok, 500_000, "TON_5", "CARGO", now.plus(Duration.ofHours(2)));            // 차종 다름

		String body = recommend()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fresh").value(true))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.id").value(first))
				.andExpect(jsonPath("$.items[0].order.pickup.name").value("칠곡 물류센터"))
				.andExpect(jsonPath("$.items[0].longWait").value(false))
				.andReturn().getResponse().getContentAsString();

		// 실수익 = 운임 − (공차 + 운송) 기름값. 공차는 세운 자리에서 시작한다. 경로를 못 받으면 통행료는 비운다
		long emptyMeters = ((Number) JsonPath.read(body, "$.items[0].emptyMeters")).longValue();
		int fuelCost = JsonPath.read(body, "$.items[0].fuelCost");
		int netProfit = JsonPath.read(body, "$.items[0].netProfit");
		assertThat(emptyMeters).isBetween(16_000L, 18_000L);   // 직선 약 13km × 1.3
		assertThat(fuelCost).isGreaterThan((int) Math.round(emptyMeters / 1000.0 / 3.5 * 1600));
		assertThat(netProfit).isEqualTo(300_000 - fuelCost);
		assertThat((Object) JsonPath.read(body, "$.items[0].tollFare")).isNull();
		assertThat(((Number) JsonPath.read(body, "$.items[0].order.distanceM")).longValue()).isEqualTo(emptyMeters);
		// TMAP에는 직선 상위 3건만 묻는다
		verify(truckRoutes).routes(org.mockito.ArgumentMatchers.argThat(legs -> legs.size() <= 6), any());
	}

	@Test
	void 같은_자리에서_다시_세우면_적어_둔_추천을_TMAP_없이_돌려주고_1km_넘게_옮기면_새로_계산한다() throws Exception {
		Integer first = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		recommend().andExpect(jsonPath("$.fresh").value(true));
		clearInvocations(truckRoutes);

		mockMvc.perform(get("/api/recommendations").param("lat", "35.843").param("lng", "128.492").header("Authorization", driver))
				.andExpect(jsonPath("$.fresh").value(false))
				.andExpect(jsonPath("$.items[0].order.id").value(first));
		verify(truckRoutes, never()).routes(any(), any());
		assertThat(shownRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/api/recommendations").param("lat", "35.86").param("lng", "128.49").header("Authorization", driver))
				.andExpect(jsonPath("$.fresh").value(true));
		verify(truckRoutes, times(1)).routes(any(), any());
		assertThat(shownRepository.count()).isEqualTo(2);
	}

	@Test
	void 상차지에서는_상차지_근처_운송_중에는_없음_하차지에서는_하차지_근처를_한_번씩_준다() throws Exception {
		Integer nearBusan = order(busan, 400_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer nearChilgok = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Trip trip = Trip.create(driverEntity, vehicleEntity, null, null, 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				busan, now, chilgok, now.plus(Duration.ofHours(5)));
		trip.start(now);
		trip.arriveAtPickup();
		tripRepository.save(trip);

		// 성서에 세웠다고 보내도 상차지(부산 신항) 근처를 준다
		recommend().andExpect(jsonPath("$.fresh").value(true))
				.andExpect(jsonPath("$.items[0].order.id").value(nearBusan));
		recommend().andExpect(jsonPath("$.fresh").value(false))
				.andExpect(jsonPath("$.items[0].order.id").value(nearBusan));

		trip.departPickup();
		recommend().andExpect(jsonPath("$.items.length()").value(0));

		trip.arriveAtDropoff();
		recommend().andExpect(jsonPath("$.fresh").value(true))
				.andExpect(jsonPath("$.items[0].order.id").value(nearChilgok));
		assertThat(shownRepository.findAll()).extracting(shown -> shown.getStopType().name())
				.containsExactly("PICKUP", "DROPOFF");
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
		assertThat(fuelCost).isEqualTo((int) Math.round(70.0 / Tonnage.TON_11.getDefaultKmPerLiter() * 1600));
		assertThat((int) JsonPath.read(body, "$.items[0].netProfit")).isEqualTo(300_000 - fuelCost - 8_000);
	}

	@Test
	void 요청을_걸어_둔_오더는_뺀다() throws Exception {
		Integer requested = order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));
		Integer other = order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)));

		// 추천에서 눌러 보낸 요청은 isRecommendation으로 남는다
		mockMvc.perform(post("/api/orders/" + requested + "/requests").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isRecommendation\": true}"))
				.andExpect(status().isCreated());
		assertThat(requestRepository.findAllByOrderIdOrderByCreatedAtAsc(requested.longValue()).getFirst().isRecommendation()).isTrue();

		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.id").value(other));
		// 추천받은 오더에 요청을 걸면 같은 자리에서 다시 받을 때 비운다
		mockMvc.perform(post("/api/orders/" + other + "/requests").header("Authorization", driver)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isRecommendation\": true}"))
				.andExpect(status().isCreated());
		recommend().andExpect(jsonPath("$.fresh").value(false))
				.andExpect(jsonPath("$.items.length()").value(0));

		// 운행이 없으면 세운 자리 근처를 준다 (부산 신항 근처에는 없다)
		mockMvc.perform(get("/api/recommendations").param("lat", "35.08").param("lng", "128.83").header("Authorization", driver))
				.andExpect(jsonPath("$.items.length()").value(0));
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

		preference("{\"enabled\": true, \"radiusKm\": 30, \"minNetProfit\": 235000}");
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
		recommend().andExpect(jsonPath("$.items[0].order.pickup.name").value("칠곡 물류센터"));

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
		recommend().andExpect(jsonPath("$.items[0].order.pickup.name").value("칠곡 물류센터"));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"excludedCargoCategories\": [\"FROZEN\"]}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 새로_더한_화물_종류도_올리고_제외할_수_있다() throws Exception {
		order(chilgok, 300_000, "TON_5", "WING", now.plus(Duration.ofHours(2)), "WASTE");
		order(bukgu, 280_000, "TON_5", "WING", now.plus(Duration.ofHours(2)), "HEAVY");
		recommend().andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].order.cargoCategoryLabel").value("폐기물"));

		preference("{\"enabled\": true, \"radiusKm\": 30, \"excludedCargoCategories\": [\"WASTE\", \"HEAVY\"]}")
				.andExpect(status().isOk());
		recommend().andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void 차주만_부르고_좌표가_잘못되면_거부한다() throws Exception {
		mockMvc.perform(get("/api/recommendations").param("lat", "35.84").param("lng", "128.49").header("Authorization", broker))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/recommendations").param("lat", "95").param("lng", "128.49").header("Authorization", driver))
				.andExpect(status().isBadRequest());
	}

	/** 대구 성서 공단에 세웠다 */
	private ResultActions recommend() throws Exception {
		return mockMvc.perform(get("/api/recommendations").param("lat", "35.84").param("lng", "128.49").header("Authorization", driver));
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
