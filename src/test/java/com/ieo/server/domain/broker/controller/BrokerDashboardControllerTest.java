package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.enums.DashboardUnit;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
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
import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitRounding;
import com.ieo.server.domain.wait.repository.WaitPolicyRepository;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.util.KoreanDate;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 콘솔 대시보드 (일·주·월 칸). 플랜은 BASIC 그대로 (모든 플랜이 본다) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerDashboardControllerTest {

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
	private BrokerMemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private WaitRecordRepository waitRecordRepository;

	@Autowired
	private WaitPolicyRepository waitPolicyRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private User owner;
	private Broker hanbit;
	private Broker rival;
	private Site pyeongtaek;
	private Site daegu;
	private LocalDate today;
	private int phoneSeq;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090009201", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		User rivalOwner = user("01090009202", UserRole.BROKER, "박대표");
		brokerService.register(rivalOwner.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));
		hanbit = broker("한빛로지스");
		rival = broker("경쟁물류");
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기도 평택시 포승읍 평택항로 156", 36.96, 126.83, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구광역시 달서구 성서공단로 1", 35.84, 128.49, null));
		today = KoreanDate.today(clock);
	}

	@Test
	void 월_단위는_6개월을_빈_달까지_채우고_완료한_달에_운임과_대기비를_더한다() throws Exception {
		Trip thisMonth = completed(driver("기사A"), hanbit, 300_000, today);
		waited(thisMonth, 180); // 무료 120분, 30분당 1만 원 → 2만 원
		completed(driver("기사B"), hanbit, 400_000, today.minusMonths(2).withDayOfMonth(3));
		completed(driver("기사C"), rival, 999_000, today); // 다른 회사
		completed(driver("기사D"), hanbit, 100_000, today, TripSource.MANUAL); // 직접 등록
		completed(driver("기사E"), hanbit, 500_000, today.minusMonths(7)); // 기간 밖
		canceled(driver("기사F"), hanbit, today.minusMonths(1));
		order(hanbit, today);
		order(hanbit, today.minusMonths(5).withDayOfMonth(1)); // 6개월 중 첫 달 첫날 → 들어간다
		order(rival, today);

		perform(get("/api/brokers/me/dashboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unit").value("MONTH"))
				.andExpect(jsonPath("$.unitLabel").value("월"))
				.andExpect(jsonPath("$.from").value(today.withDayOfMonth(1).minusMonths(5).toString()))
				.andExpect(jsonPath("$.to").value(today.toString()))
				.andExpect(jsonPath("$.buckets.length()").value(6))
				.andExpect(jsonPath("$.buckets[0].start").value(today.withDayOfMonth(1).minusMonths(5).toString()))
				.andExpect(jsonPath("$.buckets[0].orderCount").value(1))
				.andExpect(jsonPath("$.buckets[3].completedCount").value(1))
				.andExpect(jsonPath("$.buckets[3].fareTotal").value(400_000))
				.andExpect(jsonPath("$.buckets[4].canceledCount").value(1))
				.andExpect(jsonPath("$.buckets[5].start").value(today.withDayOfMonth(1).toString()))
				.andExpect(jsonPath("$.buckets[5].end").value(today.toString()))
				.andExpect(jsonPath("$.buckets[5].orderCount").value(1))
				.andExpect(jsonPath("$.buckets[5].completedCount").value(1))
				.andExpect(jsonPath("$.buckets[5].fareTotal").value(300_000))
				.andExpect(jsonPath("$.buckets[5].waitFeeTotal").value(20_000))
				.andExpect(jsonPath("$.total.orderCount").value(2))
				.andExpect(jsonPath("$.total.completedCount").value(2))
				.andExpect(jsonPath("$.total.canceledCount").value(1))
				.andExpect(jsonPath("$.total.fareTotal").value(700_000))
				.andExpect(jsonPath("$.total.waitFeeTotal").value(20_000));
	}

	@Test
	void 청구운임을_적은_완료_운행만_청구_합계와_수수료에_더한다() throws Exception {
		completedFromOrder(driver("기사A"), 450_000, 600_000, today);                       // 수수료 150,000
		completedFromOrder(driver("기사B"), 300_000, null, today);                          // 청구 없음 → 수수료 없음
		completedFromOrder(driver("기사C"), 200_000, 250_000, today.minusMonths(1));        // 수수료 50,000
		completed(driver("기사D"), hanbit, 100_000, today);                                 // 오더 없는 운행

		perform(get("/api/brokers/me/dashboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.buckets[5].completedCount").value(3))
				.andExpect(jsonPath("$.buckets[5].fareTotal").value(850_000))
				.andExpect(jsonPath("$.buckets[5].pricedCount").value(1))
				.andExpect(jsonPath("$.buckets[5].billedTotal").value(600_000))
				.andExpect(jsonPath("$.buckets[5].feeTotal").value(150_000))
				.andExpect(jsonPath("$.buckets[4].feeTotal").value(50_000))
				.andExpect(jsonPath("$.buckets[3].pricedCount").value(0))
				.andExpect(jsonPath("$.total.completedCount").value(4))
				.andExpect(jsonPath("$.total.fareTotal").value(1_050_000))
				.andExpect(jsonPath("$.total.pricedCount").value(2))
				.andExpect(jsonPath("$.total.billedTotal").value(850_000))
				.andExpect(jsonPath("$.total.feeTotal").value(200_000));
	}

	@Test
	void 주_단위는_월요일부터_끊고_일_단위는_하루씩이며_기간을_고를_수_있다() throws Exception {
		LocalDate monday = today.minusWeeks(2).with(DayOfWeek.MONDAY);
		completed(driver("기사A"), hanbit, 300_000, monday);
		completed(driver("기사B"), hanbit, 200_000, monday.plusDays(6)); // 같은 주 일요일
		completed(driver("기사C"), hanbit, 100_000, monday.plusDays(7)); // 다음 주 월요일

		perform(get("/api/brokers/me/dashboard").param("unit", "WEEK")
				.param("from", monday.plusDays(2).toString()).param("to", monday.plusDays(8).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.buckets.length()").value(2))
				.andExpect(jsonPath("$.buckets[0].start").value(monday.plusDays(2).toString())) // 기간 안으로 잘림
				.andExpect(jsonPath("$.buckets[0].end").value(monday.plusDays(6).toString()))
				.andExpect(jsonPath("$.buckets[0].completedCount").value(1))
				.andExpect(jsonPath("$.buckets[0].fareTotal").value(200_000))
				.andExpect(jsonPath("$.buckets[1].start").value(monday.plusDays(7).toString()))
				.andExpect(jsonPath("$.buckets[1].end").value(monday.plusDays(8).toString()))
				.andExpect(jsonPath("$.buckets[1].completedCount").value(1))
				.andExpect(jsonPath("$.total.completedCount").value(2));

		perform(get("/api/brokers/me/dashboard").param("unit", "DAY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unitLabel").value("일"))
				.andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
				.andExpect(jsonPath("$.buckets.length()").value(30))
				.andExpect(jsonPath("$.buckets[29].start").value(today.toString()))
				.andExpect(jsonPath("$.total.completedCount").value(3));

		perform(get("/api/brokers/me/dashboard").param("unit", "WEEK"))
				.andExpect(jsonPath("$.from").value(today.minusDays(7 * 12 - 1).toString()));
	}

	@Test
	void 잘못된_단위나_기간은_400_소속_없으면_404() throws Exception {
		perform(get("/api/brokers/me/dashboard").param("unit", "YEAR")).andExpect(status().isBadRequest());
		perform(get("/api/brokers/me/dashboard").param("from", "2026-02-01").param("to", "2026-01-01"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/brokers/me/dashboard").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + jwtProvider.issue(user("01090009203", UserRole.DRIVER, "차주").getId()).accessToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
	}

	private Trip completed(Driver driver, Broker broker, int fare, LocalDate completedOn) {
		return completed(driver, broker, fare, completedOn, TripSource.IEO_DISPATCH);
	}

	/** completedOn 낮 12시(한국)에 끝난 운행. 8시간 전에 출발 */
	private Trip completed(Driver driver, Broker broker, int fare, LocalDate completedOn, TripSource source) {
		Instant completedAt = completedOn.atTime(LocalTime.NOON).atZone(KoreanDate.ZONE).toInstant();
		Instant startedAt = completedAt.minus(Duration.ofHours(8));
		Trip trip = trip(driver, broker, fare, source, startedAt.plus(Duration.ofHours(1)), completedAt);
		trip.start(startedAt);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(completedAt);
		return trip;
	}

	private void canceled(Driver driver, Broker broker, LocalDate canceledOn) {
		Instant canceledAt = canceledOn.atTime(LocalTime.NOON).atZone(KoreanDate.ZONE).toInstant();
		trip(driver, broker, 300_000, TripSource.IEO_DISPATCH, canceledAt.plus(Duration.ofHours(1)),
				canceledAt.plus(Duration.ofHours(9))).cancel(canceledAt);
	}

	private Trip trip(Driver driver, Broker broker, int fare, TripSource source, Instant pickupAt, Instant dropoffAt) {
		Vehicle vehicle = vehicleRepository.findAll().stream()
				.filter(found -> found.isOwnedBy(driver.getUserId())).findFirst().orElseThrow();
		return tripRepository.save(Trip.create(driver, vehicle, broker, null, fare, PaymentType.RECEIPT, source,
				pyeongtaek, pickupAt, daegu, dropoffAt));
	}

	/** 상차지 예정 시각에 진입해 waitMinutes분 기다림. 대기비는 한빛 정책(무료 120분, 30분당 1만 원)으로 */
	private void waited(Trip trip, int waitMinutes) {
		WaitPolicy policy = waitPolicyRepository.save(WaitPolicy.create(hanbit, 120, 30, 10_000, 50_000, WaitRounding.UP));
		Instant arrivedAt = trip.pickup().getPlannedAt();
		waitRecordRepository.save(WaitRecord.start(trip.pickup(), arrivedAt, policy))
				.startWork(arrivedAt.plus(Duration.ofMinutes(waitMinutes)));
	}

	/** 오더로 배차된 완료 운행 (청구운임이 있으면 수수료가 잡힌다) */
	private void completedFromOrder(Driver driver, int fare, Integer billedFare, LocalDate completedOn) {
		Order order = order(hanbit, completedOn, fare, billedFare);
		Vehicle vehicle = vehicleRepository.findAll().stream()
				.filter(found -> found.isOwnedBy(driver.getUserId())).findFirst().orElseThrow();
		Instant completedAt = completedOn.atTime(LocalTime.NOON).atZone(KoreanDate.ZONE).toInstant();
		Instant startedAt = completedAt.minus(Duration.ofHours(8));
		Trip trip = tripRepository.save(Trip.fromDispatch(DispatchRequest.create(order, driver, vehicle)));
		trip.start(startedAt);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(completedAt);
	}

	private void order(Broker broker, LocalDate createdOn) {
		order(broker, createdOn, 300_000, null);
	}

	/** createdOn 낮 12시에 등록한 오더. 등록 시각은 @CreatedDate가 지금으로 찍어서 저장 뒤 쿼리로 바꾼다 */
	private Order order(Broker broker, LocalDate createdOn, int fare, Integer billedFare) {
		Instant pickupFrom = clock.instant().plus(Duration.ofDays(1));
		Order order = orderRepository.save(Order.draft()
				.createdBy(memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(broker.getId()).getFirst())
				.pickupSite(pyeongtaek)
				.dropoffSite(daegu)
				.pickupFrom(pickupFrom)
				.pickupUntil(pickupFrom.plus(Duration.ofHours(2)))
				.dropoffBy(pickupFrom.plus(Duration.ofHours(8)))
				.cargoCategory(CargoCategory.GENERAL)
				.cargoType("철근")
				.weightKg(4500)
				.tonnage(Tonnage.TON_5)
				.bodyType(BodyType.CARGO)
				.loadingMethod(LoadingMethod.FORKLIFT)
				.fare(fare)
				.billedFare(billedFare)
				.customerName(billedFare == null ? null : "대한제강")
				.paymentType(PaymentType.RECEIPT)
				.expectedPaymentDays(30)
				.consignmentType(ConsignmentType.DIRECT)
				.create());
		orderRepository.flush();
		entityManager.createQuery("update Order o set o.createdAt = :at where o.id = :id")
				.setParameter("at", createdOn.atTime(LocalTime.NOON).atZone(KoreanDate.ZONE).toInstant())
				.setParameter("id", order.getId())
				.executeUpdate();
		entityManager.clear();
		return orderRepository.findById(order.getId()).orElseThrow();
	}

	private Broker broker(String name) {
		return brokerRepository.findAll().stream().filter(b -> b.getName().equals(name)).findFirst().orElseThrow();
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + jwtProvider.issue(owner.getId()).accessToken()));
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
