package com.ieo.server.domain.wait.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class WaitControllerTest {

	/** 상차 예정 시각. 테스트 시각은 모두 과거라 미래 시각 검사에 걸리지 않는다 */
	private static final Instant PLANNED = Instant.parse("2026-01-10T01:00:00Z");

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
	private BrokerMemberRepository brokerMemberRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String bearer;
	private String ownerBearer;
	private Broker broker;
	private Trip trip;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090001001", UserRole.DRIVER));
		Driver driver = driverRepository.save(Driver.of(user));
		Vehicle vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();

		broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		User owner = userRepository.save(User.create("01090001002", UserRole.BROKER));
		brokerMemberRepository.save(BrokerMember.of(owner, broker, BrokerMemberPosition.OWNER));
		ownerBearer = "Bearer " + jwtProvider.issue(owner.getId()).accessToken();

		Site pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시 포승읍", 36.98, 126.84, null));
		Site dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		trip = Trip.create(driver, vehicle, broker, null, 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pickup, PLANNED, dropoff, null);
		trip.start(PLANNED.minusSeconds(4 * 3600));
		tripRepository.save(trip);
	}

	@Test
	void 상차부터_하차까지_대기를_기록하면_운행_상태도_따라간다() throws Exception {
		putPolicy("{\"freeMinutes\":120,\"unitMinutes\":30,\"unitAmount\":10000,\"maxAmount\":50000,\"rounding\":\"UP\"}")
				.andExpect(status().isCreated());

		// 예정 10:00(KST)보다 3시간 일찍 도착, 12:30 상차 시작, 13:10 출발
		event("PICKUP", "arrive", PLANNED.minusSeconds(3 * 3600))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andExpect(jsonPath("$.policy.unitAmount").value(10000))
				.andExpect(jsonPath("$.chargeableAmount").doesNotExist());
		tripStatus("AT_PICKUP");

		event("PICKUP", "work-start", PLANNED.plusSeconds(150 * 60))
				.andExpect(jsonPath("$.status").value("WORKING"))
				.andExpect(jsonPath("$.waitMinutes").value(330))
				.andExpect(jsonPath("$.chargeableWaitMinutes").value(150))
				.andExpect(jsonPath("$.chargeableAmount").value(10000));

		event("PICKUP", "depart", PLANNED.plusSeconds(190 * 60))
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.workMinutes").value(40))
				.andExpect(jsonPath("$.stayMinutes").value(370));
		tripStatus("IN_TRANSIT");

		event("DROPOFF", "arrive", PLANNED.plusSeconds(6 * 3600))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.plannedAtMissing").value(true));
		tripStatus("AT_DROPOFF");
		event("DROPOFF", "depart", PLANNED.plusSeconds(7 * 3600))
				.andExpect(jsonPath("$.status").value("DONE"));
		tripStatus("COMPLETED");

		mockMvc.perform(get("/api/trips/" + trip.getId() + "/waits").header("Authorization", bearer))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].stopType").value("PICKUP"))
				.andExpect(jsonPath("$[1].stopType").value("DROPOFF"));
	}

	@Test
	void 진입을_다시_보내도_기록은_하나이고_200으로_같은_기록을_돌려준다() throws Exception {
		Integer id = JsonPath.read(event("PICKUP", "arrive", PLANNED).andReturn().getResponse().getContentAsString(), "$.id");

		event("PICKUP", "arrive", PLANNED.plusSeconds(60))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.arrivedAt").value("2026-01-10T01:00:00Z"));
	}

	@Test
	void 정책이_없으면_시간만_기록한다() throws Exception {
		event("PICKUP", "arrive", PLANNED);
		event("PICKUP", "work-start", PLANNED.plusSeconds(3 * 3600))
				.andExpect(jsonPath("$.chargeableWaitMinutes").value(180))
				.andExpect(jsonPath("$.policy").doesNotExist())
				.andExpect(jsonPath("$.chargeableAmount").doesNotExist());
	}

	@Test
	void 순서나_시각이_맞지_않으면_거부한다() throws Exception {
		event("DROPOFF", "arrive", PLANNED)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRIP_409_STATUS"));
		event("PICKUP", "work-start", PLANNED)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("WAIT_404"));
		event("PICKUP", "arrive", Instant.now().plusSeconds(3600))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WAIT_400_TIME"));

		event("PICKUP", "arrive", PLANNED);
		event("PICKUP", "work-start", PLANNED.minusSeconds(60))
				.andExpect(jsonPath("$.code").value("WAIT_400_TIME"));
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/pickup/wait/arrive").header("Authorization", bearer))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 상차_시작을_나중에_넣으면_수정됨과_원래_값이_남는다() throws Exception {
		event("PICKUP", "arrive", PLANNED);
		event("PICKUP", "depart", PLANNED.plusSeconds(4 * 3600));

		mockMvc.perform(patch("/api/trips/" + trip.getId() + "/stops/PICKUP/wait").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"arrivedAt\":\"%s\",\"workStartedAt\":\"%s\",\"departedAt\":\"%s\"}"
								.formatted(PLANNED, PLANNED.plusSeconds(3 * 3600), PLANNED.plusSeconds(4 * 3600))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.edited").value(true))
				.andExpect(jsonPath("$.editedAt").exists())
				.andExpect(jsonPath("$.original.workStartedAt").doesNotExist())
				.andExpect(jsonPath("$.original.arrivedAt").value("2026-01-10T01:00:00Z"))
				.andExpect(jsonPath("$.waitMinutes").value(180));
	}

	@Test
	void 증빙_링크는_이탈한_기록만_만들고_로그인_없이_열린다() throws Exception {
		event("PICKUP", "arrive", PLANNED);
		proof().andExpect(status().isConflict());

		event("PICKUP", "work-start", PLANNED.plusSeconds(3 * 3600));
		event("PICKUP", "depart", PLANNED.plusSeconds(4 * 3600));
		String path = JsonPath.read(proof().andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.path");
		proof().andExpect(jsonPath("$.path").value(path));

		mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$.siteAddress").value("경기 평택시 포승읍"))
				.andExpect(jsonPath("$.record.siteName").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.record.waitMinutes").value(180));
		mockMvc.perform(get("/api/public/wait-proofs/없는토큰"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("WAIT_404_PROOF"));
	}

	@Test
	void 대기비_정책은_대표만_바꾸고_이전_정책은_꺼진다() throws Exception {
		mockMvc.perform(get("/api/brokers/me/wait-policy").header("Authorization", ownerBearer))
				.andExpect(status().isNoContent());
		putPolicy("{\"freeMinutes\":60,\"unitMinutes\":30,\"unitAmount\":5000,\"maxAmount\":30000,\"rounding\":\"DOWN\"}");
		putPolicy("{\"freeMinutes\":120,\"unitMinutes\":30,\"unitAmount\":10000,\"maxAmount\":50000,\"rounding\":\"UP\"}");

		mockMvc.perform(get("/api/brokers/me/wait-policy").header("Authorization", ownerBearer))
				.andExpect(jsonPath("$.freeMinutes").value(120))
				.andExpect(jsonPath("$.active").value(true));
		putPolicy("{\"freeMinutes\":120,\"unitMinutes\":30,\"unitAmount\":10000,\"maxAmount\":5000,\"rounding\":\"UP\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WAIT_400_POLICY"));

		User staff = userRepository.save(User.create("01090001003", UserRole.BROKER));
		brokerMemberRepository.save(BrokerMember.of(staff, broker, BrokerMemberPosition.STAFF));
		mockMvc.perform(put("/api/brokers/me/wait-policy")
						.header("Authorization", "Bearer " + jwtProvider.issue(staff.getId()).accessToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"freeMinutes\":0,\"unitMinutes\":30,\"unitAmount\":10000,\"maxAmount\":50000,\"rounding\":\"UP\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("WAIT_403_POLICY"));
		mockMvc.perform(get("/api/brokers/me/wait-policy").header("Authorization", bearer))
				.andExpect(status().isForbidden());
	}

	@Test
	void 하차지에서_운행을_직접_완료하면_대기_기록도_끝난다() throws Exception {
		event("PICKUP", "arrive", PLANNED);
		event("PICKUP", "depart", PLANNED.plusSeconds(3600));
		event("DROPOFF", "arrive", PLANNED.plusSeconds(5 * 3600));

		mockMvc.perform(post("/api/trips/" + trip.getId() + "/complete").header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
		mockMvc.perform(get("/api/trips/" + trip.getId() + "/waits").header("Authorization", bearer))
				.andExpect(jsonPath("$[1].status").value("DONE"));
	}

	private ResultActions event(String stopType, String action, Instant at) throws Exception {
		return mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/" + stopType + "/wait/" + action)
				.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"at\":\"" + at + "\"}"));
	}

	private ResultActions proof() throws Exception {
		return mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/PICKUP/wait/proof").header("Authorization", bearer));
	}

	private ResultActions putPolicy(String json) throws Exception {
		return mockMvc.perform(put("/api/brokers/me/wait-policy").header("Authorization", ownerBearer)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private void tripStatus(String expected) throws Exception {
		mockMvc.perform(get("/api/trips/" + trip.getId()).header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value(expected));
	}
}
