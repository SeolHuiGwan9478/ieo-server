package com.ieo.server.domain.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
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
class TripControllerTest {

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
	private TripRepository tripRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private Driver driver;
	private Vehicle vehicle;
	private String bearer;
	private Site pyeongtaek;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090000801", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		vehicle = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시 포승읍", 36.98, 126.84, null));
	}

	@Test
	void 기존_상하차지와_새_주소로_운행을_등록하면_기본_차량으로_예약된다() throws Exception {
		register("""
				{"pickup": {"siteId": %d, "plannedAt": "2026-11-03T00:00:00Z"},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "fare": 450000, "paymentType": "RECEIPT", "brokerName": "한빛로지스"}
				""".formatted(pyeongtaek.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.source").value("MANUAL"))
				.andExpect(jsonPath("$.fare").value(450000))
				.andExpect(jsonPath("$.paymentTypeLabel").value("인수증"))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.pickup.type").value("PICKUP"))
				.andExpect(jsonPath("$.pickup.siteId").value(pyeongtaek.getId()))
				.andExpect(jsonPath("$.pickup.plannedAt").value("2026-11-03T00:00:00Z"))
				.andExpect(jsonPath("$.pickup.geofenceRadiusM").value(200))
				.andExpect(jsonPath("$.dropoff.name").value("대구 성서 공단"))
				.andExpect(jsonPath("$.etaNoticeEnabled").value(true));
	}

	@Test
	void 새_주소가_100m_안의_기존_상하차지면_그것을_쓰고_Ieo_주선사는_상호를_보여준다() throws Exception {
		Broker broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));

		register("""
				{"pickup": {"site": {"name": "포승 센터 정문", "address": "경기 평택시", "lat": 36.9804, "lng": 126.84}},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "fare": 450000, "paymentType": "COLLECT", "brokerId": %d, "brokerName": "무시됨"}
				""".formatted(broker.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pickup.siteId").value(pyeongtaek.getId()))
				.andExpect(jsonPath("$.pickup.name").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.brokerId").value(broker.getId()))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"));
	}

	@Test
	void 상하차지나_차량이나_주선사가_없으면_거부한다() throws Exception {
		register("""
				{"pickup": {}, "dropoff": {"siteId": %d}, "fare": 1, "paymentType": "PREPAID"}
				""".formatted(pyeongtaek.getId()))
				.andExpect(status().isBadRequest());
		register("""
				{"pickup": {"siteId": 999999}, "dropoff": {"siteId": %d}, "fare": 1, "paymentType": "PREPAID"}
				""".formatted(pyeongtaek.getId()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SITE_404"));
		register("""
				{"pickup": {"siteId": %1$d}, "dropoff": {"siteId": %1$d}, "fare": 1, "paymentType": "PREPAID", "brokerId": 999999}
				""".formatted(pyeongtaek.getId()))
				.andExpect(jsonPath("$.code").value("BROKER_404"));
		register("""
				{"pickup": {"siteId": %1$d}, "dropoff": {"siteId": %1$d}, "fare": -1, "paymentType": "PREPAID"}
				""".formatted(pyeongtaek.getId()))
				.andExpect(jsonPath("$.code").value("TRIP_400_FARE"));

		User noCar = userRepository.save(User.create("01090000802", UserRole.DRIVER));
		driverRepository.save(Driver.of(noCar));
		mockMvc.perform(post("/api/trips").header("Authorization", "Bearer " + jwtProvider.issue(noCar.getId()).accessToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pickup\":{\"siteId\":%1$d},\"dropoff\":{\"siteId\":%1$d},\"fare\":1,\"paymentType\":\"PREPAID\"}"
								.formatted(pyeongtaek.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TRIP_400_VEHICLE"));
	}

	@Test
	void 출발하면_진행_중인_운행이_되고_두_번째_운행은_출발할_수_없다() throws Exception {
		Integer first = idOf(registerSimple());
		Integer second = idOf(registerSimple());

		mockMvc.perform(get("/api/trips/current").header("Authorization", bearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/trips/" + first + "/start").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TO_PICKUP"))
				.andExpect(jsonPath("$.startedAt").exists());
		mockMvc.perform(get("/api/trips/current").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(first))
				.andExpect(jsonPath("$.pickup.lat").value(36.98));

		mockMvc.perform(post("/api/trips/" + second + "/start").header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRIP_409_IN_PROGRESS"));
	}

	@Test
	void 하차지에_도착하기_전에는_완료할_수_없고_취소는_된다() throws Exception {
		Integer id = idOf(registerSimple());
		mockMvc.perform(post("/api/trips/" + id + "/start").header("Authorization", bearer));

		mockMvc.perform(post("/api/trips/" + id + "/complete").header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRIP_409_STATUS"));
		mockMvc.perform(post("/api/trips/" + id + "/cancel").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		mockMvc.perform(get("/api/trips/current").header("Authorization", bearer))
				.andExpect(status().isNoContent());
	}

	@Test
	void 하차지에_도착한_운행은_완료된다() throws Exception {
		Integer id = idOf(registerSimple());
		Trip trip = tripRepository.findById(id.longValue()).orElseThrow();
		Instant now = Instant.parse("2026-11-02T00:00:00Z");
		trip.start(now);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();

		mockMvc.perform(post("/api/trips/" + id + "/complete").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.completedAt").exists());
	}

	@Test
	void 목록은_최근_순이고_남의_운행은_404() throws Exception {
		Integer first = idOf(registerSimple());
		Integer second = idOf(registerSimple());

		mockMvc.perform(get("/api/trips").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/trips").header("Authorization", bearer).param("size", "1"))
				.andExpect(jsonPath("$.length()").value(1));

		User other = userRepository.save(User.create("01090000803", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		String otherBearer = "Bearer " + jwtProvider.issue(other.getId()).accessToken();
		mockMvc.perform(get("/api/trips/" + first).header("Authorization", otherBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRIP_404"));
		mockMvc.perform(post("/api/trips/" + second + "/cancel").header("Authorization", otherBearer))
				.andExpect(status().isNotFound());
	}

	private ResultActions registerSimple() throws Exception {
		return register("""
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "fare": 450000, "paymentType": "RECEIPT"}
				""".formatted(pyeongtaek.getId()));
	}

	private ResultActions register(String json) throws Exception {
		return mockMvc.perform(post("/api/trips").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
