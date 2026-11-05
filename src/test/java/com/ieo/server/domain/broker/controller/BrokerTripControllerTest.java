package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import com.ieo.server.domain.delivery.repository.DeliveryProofRepository;
import com.ieo.server.domain.delivery.repository.ProofPhotoRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.dto.TripLocationRequest;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.trip.service.TripLocationService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitProofRepository;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerTripControllerTest {

	private static final Instant T0 = Instant.parse("2026-11-04T00:00:00Z");

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
	private BrokerService brokerService;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private WaitRecordRepository waitRecordRepository;

	@Autowired
	private WaitProofRepository waitProofRepository;

	@Autowired
	private DeliveryProofRepository deliveryProofRepository;

	@Autowired
	private ProofPhotoRepository photoRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private TripLocationService locationService;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private User owner;
	private Broker hanbit;
	private Site pickup;
	private Site dropoff;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090007101", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		hanbit = brokerRepository.findAll().stream().filter(b -> b.getName().equals("한빛로지스")).findFirst().orElseThrow();
		pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 탭별로_우리_오더로_배차된_운행만_보인다() throws Exception {
		Driver driver = driver("01090007001", "김기사");
		Trip scheduled = trip(driver, hanbit, TripSource.IEO_DISPATCH);
		Trip moving = trip(driver("01090007002", "이기사"), hanbit, TripSource.IEO_DISPATCH);
		moving.start(T0);
		Trip done = trip(driver("01090007003", "박기사"), hanbit, TripSource.IEO_DISPATCH);
		finish(done);
		// 차주가 직접 등록하면서 우리 회사를 고른 운행은 차주 장부라 안 보인다
		Trip manual = trip(driver("01090007004", "최기사"), hanbit, TripSource.MANUAL);
		manual.start(T0);

		perform(get("/api/brokers/me/trips"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.items[0].id").value(moving.getId()))
				.andExpect(jsonPath("$.items[0].status").value("TO_PICKUP"))
				.andExpect(jsonPath("$.items[0].statusLabel").value("상차지로 이동 중"))
				.andExpect(jsonPath("$.items[0].driverName").value("이기사"))
				.andExpect(jsonPath("$.items[0].plateNumber").value("경기82사7002"))
				.andExpect(jsonPath("$.items[0].pickup.siteName").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.items[0].pickup.arrivedAt").doesNotExist());
		perform(get("/api/brokers/me/trips").param("tab", "SCHEDULED"), owner)
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(scheduled.getId()));
		perform(get("/api/brokers/me/trips").param("tab", "FINISHED"), owner)
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
		perform(get("/api/brokers/me/trips/{id}", manual.getId()), owner)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRIP_404"));
	}

	@Test
	void 단골_차주만_위치를_볼_수_있고_번호가_보인다() throws Exception {
		Driver regular = driver("01090007001", "김기사");
		Driver stranger = driver("01090007002", "이기사");
		link("01090007001", regular);
		Trip regularTrip = trip(regular, hanbit, TripSource.IEO_DISPATCH);
		Trip strangerTrip = trip(stranger, hanbit, TripSource.IEO_DISPATCH);

		// 아직 위치가 안 왔으면 모두 비어 있다
		perform(get("/api/brokers/me/trips/{id}", regularTrip.getId()), owner)
				.andExpect(jsonPath("$.linkedDriver").value(true))
				.andExpect(jsonPath("$.locationVisible").value(true))
				.andExpect(jsonPath("$.driverPhone").value("01090007001"))
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.etaAt").doesNotExist());

		// 차량 앱이 보낸 마지막 위치: 상차지로 가는 길, 남은 200km·2시간 30분 (처음 거리 250km → 20%)
		Instant recordedAt = clock.instant().minusSeconds(60);
		regularTrip.start(recordedAt.minusSeconds(600));
		strangerTrip.start(recordedAt.minusSeconds(600));
		sendLocation(regularTrip, recordedAt.minusSeconds(300), 250_000, 11_000);
		sendLocation(regularTrip, recordedAt, 200_000, 9_000);
		sendLocation(strangerTrip, recordedAt, 200_000, 9_000);

		perform(get("/api/brokers/me/trips/{id}", regularTrip.getId()), owner)
				.andExpect(jsonPath("$.location.lat").value(36.5))
				.andExpect(jsonPath("$.location.lng").value(127.0))
				.andExpect(jsonPath("$.location.recordedAt").value(recordedAt.toString()))
				.andExpect(jsonPath("$.etaAt").value(recordedAt.plusSeconds(9_000).toString()))
				.andExpect(jsonPath("$.progressPercent").value(20));
		// 단골이 아니면 위치는 숨기고 ETA·진행률만
		perform(get("/api/brokers/me/trips/{id}", strangerTrip.getId()), owner)
				.andExpect(jsonPath("$.linkedDriver").value(false))
				.andExpect(jsonPath("$.locationVisible").value(false))
				.andExpect(jsonPath("$.driverPhone").doesNotExist())
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.etaAt").value(recordedAt.plusSeconds(9_000).toString()));
		perform(get("/api/brokers/me/trips"), owner)
				.andExpect(jsonPath("$.items[?(@.id == " + regularTrip.getId() + ")].location.lat").value(Matchers.contains(36.5)));
	}


	@Test
	void 도착_시각은_바로_보이고_대기비는_증빙을_만든_뒤에_보인다() throws Exception {
		Trip trip = trip(driver("01090007001", "김기사"), hanbit, TripSource.IEO_DISPATCH);
		trip.start(T0);
		trip.arriveAtPickup();
		WaitRecord record = waitRecordRepository.save(WaitRecord.start(trip.pickup(), T0.plusSeconds(3600), null));
		record.startWork(T0.plusSeconds(3600 * 3));

		perform(get("/api/brokers/me/trips/{id}", trip.getId()), owner)
				.andExpect(jsonPath("$.pickup.waitStatus").value("WORKING"))
				.andExpect(jsonPath("$.pickup.arrivedAt").value("2026-11-04T01:00:00Z"))
				.andExpect(jsonPath("$.pickup.workStartedAt").value("2026-11-04T03:00:00Z"))
				.andExpect(jsonPath("$.pickup.waitProof").doesNotExist())
				.andExpect(jsonPath("$.dropoff.waitStatus").doesNotExist());

		WaitProof proof = waitProofRepository.save(WaitProof.create(record, T0.plusSeconds(86400 * 30)));
		perform(get("/api/brokers/me/trips"), owner)
				.andExpect(jsonPath("$.items[0].pickup.waitProof.path").value("/w/" + proof.getShareToken()))
				.andExpect(jsonPath("$.items[0].pickup.waitProof.waitMinutes").value(120))
				.andExpect(jsonPath("$.items[0].pickup.waitProof.expiresAt").exists());
	}

	@Test
	void 인수증은_서명하거나_종이_사진을_올린_뒤에_보인다() throws Exception {
		Trip signed = trip(driver("01090007001", "김기사"), hanbit, TripSource.IEO_DISPATCH);
		DeliveryProof proof = deliveryProofRepository.save(DeliveryProof.issue(signed, null, null));
		perform(get("/api/brokers/me/trips/{id}", signed.getId()), owner)
				.andExpect(jsonPath("$.receipt").doesNotExist());

		proof.sign("홍길동", "sig/key.png", null, null, null, T0);
		perform(get("/api/brokers/me/trips/{id}", signed.getId()), owner)
				.andExpect(jsonPath("$.receipt.status").value("SIGNED"))
				.andExpect(jsonPath("$.receipt.signerName").value("홍길동"))
				.andExpect(jsonPath("$.receipt.path").value("/r/" + proof.getToken()))
				.andExpect(jsonPath("$.receipt.hasPaperPhoto").value(false));

		Trip paper = trip(driver("01090007002", "이기사"), hanbit, TripSource.IEO_DISPATCH);
		DeliveryProof paperProof = deliveryProofRepository.save(DeliveryProof.issue(paper, null, null));
		photoRepository.save(ProofPhoto.of(paperProof, ProofPhotoType.PAPER, "paper/key.jpg"));
		perform(get("/api/brokers/me/trips").param("tab", "SCHEDULED"), owner)
				.andExpect(jsonPath("$.items[?(@.id == " + paper.getId() + ")].receipt.status").value(Matchers.contains("PHOTO")));
	}

	@Test
	void 다른_회사_운행은_404이고_목록에도_없다() throws Exception {
		Trip ours = trip(driver("01090007001", "김기사"), hanbit, TripSource.IEO_DISPATCH);
		ours.start(T0);
		User rival = user("01090007102", UserRole.BROKER, "박대표");
		brokerService.register(rival.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));

		perform(get("/api/brokers/me/trips/{id}", ours.getId()), rival)
				.andExpect(status().isNotFound());
		perform(get("/api/brokers/me/trips"), rival)
				.andExpect(jsonPath("$.totalCount").value(0));
		perform(get("/api/brokers/me/trips"), user("01090007103", UserRole.DRIVER, "차주"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
	}

	/** 차량 앱이 위치를 보내는 것과 같은 길 (원작자의 TripLocationService) */
	private void sendLocation(Trip trip, Instant recordedAt, int remainMeters, int remainSeconds) {
		locationService.record(trip, new TripLocationRequest(List.of(
				new TripLocationRequest.Point(recordedAt, 36.5, 127.0, remainMeters, remainSeconds))));
	}

	private void finish(Trip trip) {
		trip.start(T0);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(T0.plusSeconds(3600 * 8));
	}

	private Trip trip(Driver driver, Broker broker, TripSource source) {
		Vehicle vehicle = vehicleRepository.findAll().stream()
				.filter(found -> found.isOwnedBy(driver.getUserId())).findFirst().orElseThrow();
		return tripRepository.save(Trip.create(driver, vehicle, broker, null, 450_000, PaymentType.RECEIPT, source,
				pickup, T0.plusSeconds(3600), dropoff, T0.plusSeconds(3600 * 8)));
	}

	/** 주선사가 요청하고 차주가 수락해 단골이 된다 (2-4 API 그대로) */
	private void link(String phone, Driver driver) throws Exception {
		String body = perform(post("/api/brokers/me/driver-invitations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"), owner)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long linkId = ((Number) JsonPath.read(body, "$.id")).longValue();
		perform(post("/api/driver-links/invitations/{id}/accept", linkId), driver.getUser())
				.andExpect(status().isOk());
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, User user) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + jwtProvider.issue(user.getId()).accessToken()));
	}

	private User user(String phone, UserRole role, String name) {
		User user = User.create(phone, role);
		user.changeName(name);
		return userRepository.save(user);
	}

	private Driver driver(String phone, String name) {
		User user = user(phone, UserRole.DRIVER, name);
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + phone.substring(7), Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return created;
	}
}
