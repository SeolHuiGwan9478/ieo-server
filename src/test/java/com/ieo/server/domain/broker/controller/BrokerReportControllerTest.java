package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.enums.CancelReason;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
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
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

/**
 * 주선사 리포트 (BRK-040~044). 테스트 설정은 최소 건수 2 (application-test.yml).
 * 시각은 지금 기준 (기간 기본값이 오늘까지 90일이라서)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerReportControllerTest {

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
	private CancellationRepository cancellationRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private User owner;
	private Broker hanbit;
	private Broker rival;
	private User rivalOwner;
	private Site pyeongtaek;
	private Site pyeongtaek2;
	private Site daegu;
	private Site busan;
	private Instant now;
	private int phoneSeq;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090009101", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		rivalOwner = user("01090009102", UserRole.BROKER, "박대표");
		brokerService.register(rivalOwner.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));
		// 리포트는 PRO부터 (BrokerPlanControllerTest). 여기서는 둘 다 PRO로
		brokerService.changePlan(owner.getId(), BrokerPlan.PRO);
		brokerService.changePlan(rivalOwner.getId(), BrokerPlan.PRO);
		hanbit = broker("한빛로지스");
		rival = broker("경쟁물류");
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기도 평택시 포승읍 평택항로 156", 36.96, 126.83, null));
		pyeongtaek2 = siteRepository.save(Site.create("평택 청북 창고", "경기 평택시 청북읍 고잔리 12", 37.01, 126.90, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구광역시 달서구 성서공단로 1", 35.84, 128.49, null));
		busan = siteRepository.save(Site.create("부산 신항", "부산 강서구 신항로 245", 35.07, 128.83, null));
		now = clock.instant();
	}

	@Test
	void 노선은_시군구로_묶고_모든_회사_완료_운행으로_운임과_소요_시간을_낸다() throws Exception {
		Driver a = driver("기사A");
		completed(a, hanbit, pyeongtaek, daegu, 300_000, 8);
		completed(driver("기사B"), hanbit, pyeongtaek, daegu, 400_000, 7);
		completed(driver("기사C"), hanbit, pyeongtaek, daegu, 500_000, 9);
		// 같은 시·군·구의 다른 창고, 다른 회사 → 같은 노선에 든다
		completed(driver("기사D"), rival, pyeongtaek2, daegu, 600_000, 8);
		// 1건뿐인 노선은 숨김 (최소 2건), 직접 등록 운행은 안 센다
		completed(driver("기사E"), hanbit, daegu, busan, 700_000, 5);
		completed(a, hanbit, pyeongtaek, daegu, 100_000, 8, TripSource.MANUAL);

		perform(get("/api/brokers/me/reports/routes"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.minSamples").value(2))
				.andExpect(jsonPath("$.routes.length()").value(1))
				.andExpect(jsonPath("$.routes[0].pickupArea").value("경기 평택시"))
				.andExpect(jsonPath("$.routes[0].dropoffArea").value("대구 달서구"))
				.andExpect(jsonPath("$.routes[0].tripCount").value(4))
				.andExpect(jsonPath("$.routes[0].avgFare").value(450_000))
				.andExpect(jsonPath("$.routes[0].medianFare").value(450_000))
				.andExpect(jsonPath("$.routes[0].minFare").value(300_000))
				.andExpect(jsonPath("$.routes[0].maxFare").value(600_000))
				.andExpect(jsonPath("$.routes[0].avgDurationMinutes").value(480))
				.andExpect(jsonPath("$.routes[0].months.length()").value(1))
				.andExpect(jsonPath("$.routes[0].months[0].tripCount").value(4));
		// 경쟁사도 같은 노선 숫자를 본다 (어느 회사 운행인지는 안 나온다)
		perform(get("/api/brokers/me/reports/routes"), rivalOwner)
				.andExpect(jsonPath("$.routes[0].tripCount").value(4));
	}

	@Test
	void 상하차지는_우리가_다니는_곳만_보이고_평균은_모든_회사_기록으로_낸다() throws Exception {
		Trip ours1 = completed(driver("기사A"), hanbit, pyeongtaek, daegu, 300_000, 8);
		Trip ours2 = completed(driver("기사B"), hanbit, pyeongtaek, daegu, 400_000, 8);
		Trip theirs = completed(driver("기사C"), rival, pyeongtaek, busan, 500_000, 8);
		waited(ours1.pickup(), 60);
		waited(ours2.pickup(), 120);
		waited(theirs.pickup(), 90);
		waited(ours1.dropoff(), 30); // 대구는 1건뿐이라 숨김
		waited(theirs.dropoff(), 40); // 부산은 우리가 안 다님

		perform(get("/api/brokers/me/reports/sites"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sites.length()").value(1))
				.andExpect(jsonPath("$.sites[0].name").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.sites[0].recordCount").value(3))
				.andExpect(jsonPath("$.sites[0].avgWaitMinutes").value(90))
				.andExpect(jsonPath("$.sites[0].maxWaitMinutes").value(120))
				.andExpect(jsonPath("$.sites[0].avgWorkMinutes").doesNotExist());
	}

	@Test
	void 내_회사_리포트는_배차_완료_취소와_차량별_이행률을_센다() throws Exception {
		Driver a = driver("기사A");
		completed(a, hanbit, pyeongtaek, daegu, 300_000, 8);
		Trip canceledByBroker = scheduled(a, hanbit, pyeongtaek, daegu);
		cancel(canceledByBroker, owner);
		Driver b = driver("기사B");
		completed(b, hanbit, pyeongtaek, daegu, 400_000, 8);
		completed(b, hanbit, daegu, pyeongtaek, 350_000, 8);
		Trip canceledByDriver = scheduled(driver("기사C"), hanbit, pyeongtaek, daegu);
		cancel(canceledByDriver, canceledByDriver.getDriver().getUser());
		completed(driver("기사D"), rival, pyeongtaek, daegu, 999_000, 8); // 다른 회사

		perform(get("/api/brokers/me/reports/company"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderCount").value(0))
				.andExpect(jsonPath("$.dispatchedCount").value(5))
				.andExpect(jsonPath("$.completedCount").value(3))
				.andExpect(jsonPath("$.canceledCount").value(2))
				.andExpect(jsonPath("$.canceledByBrokerCount").value(1))
				.andExpect(jsonPath("$.canceledByDriverCount").value(1))
				.andExpect(jsonPath("$.avgConfirmMinutes").doesNotExist())
				.andExpect(jsonPath("$.waitFeeTotal").value(0))
				.andExpect(jsonPath("$.vehicles.length()").value(3))
				.andExpect(jsonPath("$.vehicles[0].driverName").value("기사A"))
				.andExpect(jsonPath("$.vehicles[0].dispatched").value(2))
				.andExpect(jsonPath("$.vehicles[0].completionPercent").value(50))
				.andExpect(jsonPath("$.vehicles[1].driverName").value("기사B"))
				.andExpect(jsonPath("$.vehicles[1].completionPercent").value(100))
				.andExpect(jsonPath("$.vehicles[2].completionPercent").value(0));
	}

	@Test
	void 차주_리포트는_정시_도착과_취소를_세고_단골만_번호가_보인다() throws Exception {
		Driver a = driver("기사A");
		link(a);
		Trip late = scheduled(a, hanbit, pyeongtaek, daegu);
		cancel(late, a.getUser()); // 상차 1시간 전 취소 → 늦은 취소
		Trip aDone = completed(a, hanbit, pyeongtaek, daegu, 300_000, 8);
		arrived(aDone.pickup(), 10); // 예정보다 10분 늦게 → 정시

		Driver b = driver("기사B");
		Trip b1 = completed(b, hanbit, pyeongtaek, daegu, 400_000, 8);
		Trip b2 = completed(b, hanbit, pyeongtaek, daegu, 400_000, 8);
		arrived(b1.pickup(), 10);
		arrived(b2.pickup(), 45); // 30분 넘게 늦음
		completed(b, hanbit, daegu, busan, 500_000, 8); // 진입 기록 없음 → 정시 계산에서 뺌

		perform(get("/api/brokers/me/reports/drivers"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.drivers.length()").value(2))
				.andExpect(jsonPath("$.drivers[0].name").value("기사B"))
				.andExpect(jsonPath("$.drivers[0].linked").value(false))
				.andExpect(jsonPath("$.drivers[0].phone").doesNotExist())
				.andExpect(jsonPath("$.drivers[0].dispatched").value(3))
				.andExpect(jsonPath("$.drivers[0].completed").value(3))
				.andExpect(jsonPath("$.drivers[0].onTimeBase").value(2))
				.andExpect(jsonPath("$.drivers[0].onTimeCount").value(1))
				.andExpect(jsonPath("$.drivers[0].onTimePercent").value(50))
				.andExpect(jsonPath("$.drivers[1].name").value("기사A"))
				.andExpect(jsonPath("$.drivers[1].linked").value(true))
				.andExpect(jsonPath("$.drivers[1].phone").value(a.getUser().getPhone()))
				.andExpect(jsonPath("$.drivers[1].canceledCount").value(1))
				.andExpect(jsonPath("$.drivers[1].lateCancelCount").value(1))
				.andExpect(jsonPath("$.drivers[1].onTimePercent").value(100));
	}

	@Test
	void 기간은_한국_날짜이고_잘못되면_400_소속_없으면_404() throws Exception {
		completed(driver("기사A"), hanbit, pyeongtaek, daegu, 300_000, 8);
		perform(get("/api/brokers/me/reports/company").param("from", "2020-01-01").param("to", "2020-01-31"), owner)
				.andExpect(jsonPath("$.from").value("2020-01-01"))
				.andExpect(jsonPath("$.dispatchedCount").value(0));
		perform(get("/api/brokers/me/reports/company").param("from", "2026-02-01").param("to", "2026-01-01"), owner)
				.andExpect(status().isBadRequest());
		perform(get("/api/brokers/me/reports/company").param("from", "2024-01-01").param("to", "2026-01-01"), owner)
				.andExpect(status().isBadRequest());
		perform(get("/api/brokers/me/reports/routes"), user("01090009103", UserRole.DRIVER, "차주"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
	}

	private Trip completed(Driver driver, Broker broker, Site pickup, Site dropoff, int fare, int hours) {
		return completed(driver, broker, pickup, dropoff, fare, hours, TripSource.IEO_DISPATCH);
	}

	/** hours시간 전에 출발해 2시간 전에 끝난 운행. 상차 예정은 출발 1시간 뒤 */
	private Trip completed(Driver driver, Broker broker, Site pickup, Site dropoff, int fare, int hours, TripSource source) {
		Instant completedAt = now.minus(Duration.ofHours(2));
		Instant startedAt = completedAt.minus(Duration.ofHours(hours));
		Trip trip = trip(driver, broker, pickup, dropoff, fare, source, startedAt.plus(Duration.ofHours(1)), completedAt);
		trip.start(startedAt);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(completedAt);
		return trip;
	}

	/** 출발 전, 상차 예정은 1시간 뒤 */
	private Trip scheduled(Driver driver, Broker broker, Site pickup, Site dropoff) {
		return trip(driver, broker, pickup, dropoff, 300_000, TripSource.IEO_DISPATCH,
				now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(9)));
	}

	private Trip trip(Driver driver, Broker broker, Site pickup, Site dropoff, int fare, TripSource source,
			Instant pickupAt, Instant dropoffAt) {
		Vehicle vehicle = vehicleRepository.findAll().stream()
				.filter(found -> found.isOwnedBy(driver.getUserId())).findFirst().orElseThrow();
		return tripRepository.save(Trip.create(driver, vehicle, broker, null, fare, PaymentType.RECEIPT, source,
				pickup, pickupAt, dropoff, dropoffAt));
	}

	private void cancel(Trip trip, User by) {
		trip.cancel(now);
		cancellationRepository.save(Cancellation.of(trip, by, CancelReason.SHIPPER_CANCELED, null));
	}

	/** 예정 시각보다 lateMinutes분 늦게 진입, 1시간 뒤 작업 시작 */
	private void arrived(TripStop stop, int lateMinutes) {
		Instant arrivedAt = stop.getPlannedAt().plus(Duration.ofMinutes(lateMinutes));
		waitRecordRepository.save(WaitRecord.start(stop, arrivedAt, null)).startWork(arrivedAt.plus(Duration.ofHours(1)));
	}

	/** 예정 시각에 진입해 waitMinutes분 기다림 */
	private void waited(TripStop stop, int waitMinutes) {
		Instant arrivedAt = stop.getPlannedAt();
		waitRecordRepository.save(WaitRecord.start(stop, arrivedAt, null)).startWork(arrivedAt.plus(Duration.ofMinutes(waitMinutes)));
	}

	private void link(Driver driver) throws Exception {
		String body = perform(post("/api/brokers/me/driver-invitations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + driver.getUser().getPhone() + "\"}"), owner)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long linkId = ((Number) JsonPath.read(body, "$.id")).longValue();
		perform(post("/api/driver-links/invitations/{id}/accept", linkId), driver.getUser())
				.andExpect(status().isOk());
	}

	private Broker broker(String name) {
		return brokerRepository.findAll().stream().filter(b -> b.getName().equals(name)).findFirst().orElseThrow();
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

	private Driver driver(String name) {
		String phone = "0109000%04d".formatted(++phoneSeq);
		User user = user(phone, UserRole.DRIVER, name);
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + phone.substring(7), Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return created;
	}
}
