package com.ieo.server.domain.broker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMetrics;
import com.ieo.server.domain.broker.repository.BrokerMetricsRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.broker.service.BrokerMetricsCalculator;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.repository.ExtraIncomeRepository;
import com.ieo.server.domain.ledger.repository.LedgerEntryRepository;
import com.ieo.server.domain.ledger.service.LedgerRecorder;
import com.ieo.server.domain.ledger.service.LedgerService;
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
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.auth.JwtProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** 주선사 신뢰 지표 (BRK-030~032). 시각은 지금 기준으로 잡는다 (계산이 오늘 날짜를 쓴다) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerMetricsControllerTest {

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
	private LedgerRecorder ledgerRecorder;

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private ExtraIncomeRepository extraIncomeRepository;

	@Autowired
	private WaitRecordRepository waitRecordRepository;

	@Autowired
	private BrokerMetricsRepository metricsRepository;

	@Autowired
	private BrokerMetricsCalculator calculator;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;
	private User owner;
	private Broker hanbit;
	private Site pickup;
	private Site dropoff;
	private Instant now;
	/** 40일 전에 끝난 운행. 정산 주기 30일이라 지급 예정일이 10일 지났다 */
	private Instant completedAt;
	private int phoneSeq;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090008101", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		hanbit = brokerRepository.findAll().stream().filter(b -> b.getName().equals("한빛로지스")).findFirst().orElseThrow();
		pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		now = clock.instant();
		completedAt = now.minus(Duration.ofDays(40));
	}

	@Test
	void 끝난_운행_20건부터_지급_지연_대기를_공개한다() throws Exception {
		List<LedgerEntry> entries = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			entries.add(finishedTrip(TripSource.IEO_DISPATCH));
		}
		// 10건은 운행 20일 뒤(예정일 전), 5건은 오늘(예정일 10일 지남) 받음, 5건은 아직 못 받음
		for (int i = 0; i < 10; i++) {
			entries.get(i).markPaid(completedAt.plus(Duration.ofDays(20)));
		}
		for (int i = 10; i < 15; i++) {
			entries.get(i).markPaid(now);
		}
		// 대기비 2건 중 1건 받음
		ExtraIncome paidFee = extraIncomeRepository.save(ExtraIncome.waitFee(entries.get(0), waitRecord(entries.get(0), 60), 10_000));
		paidFee.markPaid(now);
		extraIncomeRepository.save(ExtraIncome.waitFee(entries.get(1), waitRecord(entries.get(1), 120), 20_000));
		calculator.recalculate(hanbit);

		perform(get("/api/brokers/me/metrics"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.published").value(true))
				.andExpect(jsonPath("$.sampleCount").value(20))
				// (10×20일 + 5×40일) / 15건 = 26.7일
				.andExpect(jsonPath("$.avgPaymentDays").value(27))
				// 예정일 지난 20건 중 늦게 받은 5건 + 못 받은 5건
				.andExpect(jsonPath("$.latePaymentPercent").value(50))
				.andExpect(jsonPath("$.waitFeePaidPercent").value(50))
				.andExpect(jsonPath("$.avgWaitMinutes").value(90))
				.andExpect(jsonPath("$.calculatedAt").exists());
	}

	@Test
	void 차주가_직접_등록한_운행과_선불_착불은_세지_않는다() {
		for (int i = 0; i < 20; i++) {
			finishedTrip(TripSource.IEO_DISPATCH).markPaid(completedAt.plus(Duration.ofDays(10)));
		}
		// 차주가 직접 등록하면서 우리 회사를 고른 운행: 못 받은 걸로 남아도 지표에 안 들어간다
		for (int i = 0; i < 5; i++) {
			finishedTrip(TripSource.MANUAL);
		}
		// 선불은 현장에서 받아 지급 기간을 따지지 않는다
		finishedTrip(TripSource.IEO_DISPATCH, PaymentType.PREPAID);
		calculator.recalculate(hanbit);

		BrokerMetrics metrics = metricsRepository.findById(hanbit.getId()).orElseThrow();
		assertThat(metrics.getSampleCount()).isEqualTo(21);
		assertThat(metrics.getAvgPaymentDays()).isEqualTo(10);
		assertThat(metrics.getLatePaymentPercent()).isZero();
		assertThat(metrics.getWaitFeePaidPercent()).isNull();
	}

	@Test
	void 표본이_20건_미만이면_값을_숨긴다() throws Exception {
		perform(get("/api/brokers/me/metrics"), owner)
				.andExpect(jsonPath("$.published").value(false))
				.andExpect(jsonPath("$.sampleCount").value(0))
				.andExpect(jsonPath("$.minSampleCount").value(20));

		for (int i = 0; i < 19; i++) {
			finishedTrip(TripSource.IEO_DISPATCH);
		}
		perform(get("/api/brokers/me/metrics"), owner)
				.andExpect(jsonPath("$.published").value(false))
				.andExpect(jsonPath("$.sampleCount").value(19))
				.andExpect(jsonPath("$.latePaymentPercent").doesNotExist());
		// 저장은 해 둔다. 숨기는 건 응답에서만
		assertThat(metricsRepository.findById(hanbit.getId()).orElseThrow().getLatePaymentPercent()).isEqualTo(100);
	}

	@Test
	void 차주가_받음을_누르면_바로_다시_계산한다() {
		List<LedgerEntry> entries = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			entries.add(finishedTrip(TripSource.IEO_DISPATCH));
		}
		// 장부가 생길 때 이미 계산됨: 전부 못 받음
		assertThat(metricsRepository.findById(hanbit.getId()).orElseThrow().getLatePaymentPercent()).isEqualTo(100);

		LedgerEntry first = entries.getFirst();
		ledgerService.setPaid(first.getDriver().getUserId(), first.getId(), true);
		assertThat(metricsRepository.findById(hanbit.getId()).orElseThrow().getLatePaymentPercent()).isEqualTo(100);
		assertThat(metricsRepository.findById(hanbit.getId()).orElseThrow().getAvgPaymentDays()).isEqualTo(40);

		ledgerService.setPaid(first.getDriver().getUserId(), first.getId(), false);
		assertThat(metricsRepository.findById(hanbit.getId()).orElseThrow().getAvgPaymentDays()).isNull();
	}

	@Test
	void 소속이_없으면_404() throws Exception {
		perform(get("/api/brokers/me/metrics"), user("01090008102", UserRole.DRIVER, "차주"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
	}

	private LedgerEntry finishedTrip(TripSource source) {
		return finishedTrip(source, PaymentType.RECEIPT);
	}

	/** 40일 전에 끝난 운행과 그 장부 (운행 완료 때와 같은 곳으로 만든다) */
	private LedgerEntry finishedTrip(TripSource source, PaymentType paymentType) {
		Driver driver = driver();
		Vehicle vehicle = vehicleRepository.findAll().stream()
				.filter(found -> found.isOwnedBy(driver.getUserId())).findFirst().orElseThrow();
		Instant startedAt = completedAt.minus(Duration.ofHours(8));
		Trip trip = tripRepository.save(Trip.create(driver, vehicle, hanbit, null, 450_000, paymentType, source,
				pickup, startedAt.plus(Duration.ofHours(1)), dropoff, completedAt));
		trip.start(startedAt);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(completedAt);
		ledgerRecorder.recordTrip(trip);
		return ledgerEntryRepository.findByTripId(trip.getId()).orElseThrow();
	}

	/** 상차지에서 waitMinutes분 기다린 기록 */
	private WaitRecord waitRecord(LedgerEntry entry, int waitMinutes) {
		Trip trip = entry.getTrip();
		Instant arrivedAt = completedAt.minus(Duration.ofHours(7));
		WaitRecord record = waitRecordRepository.save(WaitRecord.start(trip.pickup(), arrivedAt, null));
		record.startWork(arrivedAt.plus(Duration.ofMinutes(waitMinutes)));
		return record;
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

	private Driver driver() {
		String phone = "0109000%04d".formatted(++phoneSeq);
		User user = user(phone, UserRole.DRIVER, "기사" + phoneSeq);
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + phone.substring(7), Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return created;
	}
}
