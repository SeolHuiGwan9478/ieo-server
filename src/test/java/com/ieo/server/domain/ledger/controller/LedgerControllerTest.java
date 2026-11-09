package com.ieo.server.domain.ledger.controller;

import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
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
import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.enums.WaitRounding;
import com.ieo.server.domain.wait.repository.WaitPolicyRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.util.KoreanDate;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
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
class LedgerControllerTest {

	/** 상차 예정 2026-01-10 10:00 KST. 지난 날짜라 미래 시각 검사에 걸리지 않는다 */
	private static final Instant PLANNED = Instant.parse("2026-01-10T01:00:00Z");
	private static final String DAY = "2026-01-10";

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
	private WaitPolicyRepository policyRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String bearer;
	private Driver driver;
	private Vehicle vehicle;
	private Broker broker;
	private Site pyeongtaek;
	private Site daegu;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090001701", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		policyRepository.save(WaitPolicy.create(broker, 120, 30, 10_000, 50_000, WaitRounding.UP));
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 기간_장부를_CSV로_내보내고_수식처럼_보이는_글자를_막는다() throws Exception {
		driver.changeBusinessInfo("한길운수", "1234567891", "홍길동");
		mockMvc.perform(post("/api/ledger").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"workDate\":\"2026-03-10\",\"description\":\"=평택, 대구\",\"fare\":300000,\"paymentType\":\"RECEIPT\",\"brokerName\":\"한빛\"}"))
				.andExpect(status().is2xxSuccessful());
		mockMvc.perform(post("/api/expenses").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"spentOn\":\"2026-03-09\",\"category\":\"FUEL\",\"amount\":50000,\"memo\":\"경유\"}"))
				.andExpect(status().is2xxSuccessful());

		var response = mockMvc.perform(get("/api/ledger/export").header("Authorization", bearer).param("from", "2026-03-01").param("to", "2026-03-31"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("ieo-ledger-2026-03-01_2026-03-31.csv")))
				.andReturn().getResponse();
		String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);

		assertThat(csv).startsWith("\uFEFF기간,2026-03-01 ~ 2026-03-31\r\n상호,한길운수\r\n사업자등록번호,1234567891\r\n");
		assertThat(csv).contains("일자,구분,항목,거래처,내용,결제 방식,수입금액,지출금액,입금 여부,입금일,주행거리(km)");
		assertThat(csv.indexOf("2026-03-09,지출,주유,,경유,,,50000")).isLessThan(csv.indexOf("2026-03-10,수입,운임,한빛,\"'=평택, 대구\""));
		assertThat(csv).contains("합계,,,,,,300000,50000");
	}

	@Test
	void 내보내기_기간이_1년을_넘으면_거절한다() throws Exception {
		mockMvc.perform(get("/api/ledger/export").header("Authorization", bearer).param("from", "2024-01-01").param("to", "2026-01-01"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 하차지를_떠나_운행이_끝나면_장부와_대기비가_자동으로_생긴다() throws Exception {
		Trip trip = startedTrip();
		// 상차: 07:00 도착(예정 10:00), 12:30 상차 시작, 13:10 출발 → 대기비 1만 원
		event(trip, "PICKUP", "arrive", PLANNED.minusSeconds(3 * 3600));
		event(trip, "PICKUP", "work-start", PLANNED.plusSeconds(150 * 60));
		event(trip, "PICKUP", "depart", PLANNED.plusSeconds(190 * 60));
		event(trip, "DROPOFF", "arrive", PLANNED.plusSeconds(6 * 3600));
		event(trip, "DROPOFF", "depart", PLANNED.plusSeconds(7 * 3600));

		mockMvc.perform(get("/api/ledger").header("Authorization", bearer).param("from", DAY).param("to", DAY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].auto").value(true))
				.andExpect(jsonPath("$[0].tripId").value(trip.getId()))
				.andExpect(jsonPath("$[0].workDate").value(DAY))
				.andExpect(jsonPath("$[0].description").value("평택 포승 물류센터 → 대구 성서 공단"))
				.andExpect(jsonPath("$[0].fare").value(450000))
				.andExpect(jsonPath("$[0].paymentTypeLabel").value("인수증"))
				.andExpect(jsonPath("$[0].brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$[0].expectedPaymentDate").value("2026-02-09"))
				.andExpect(jsonPath("$[0].waitMinutes").value(330))
				.andExpect(jsonPath("$[0].overdue").value(true))
				.andExpect(jsonPath("$[0].extraIncomes.length()").value(1))
				.andExpect(jsonPath("$[0].extraIncomes[0].stopType").value("PICKUP"))
				.andExpect(jsonPath("$[0].extraIncomes[0].amount").value(10000))
				.andExpect(jsonPath("$[0].extraIncomes[0].paid").value(false));
	}

	@Test
	void 완료_버튼으로_끝내도_장부가_생기고_두_번_생기지_않는다() throws Exception {
		Trip trip = startedTrip();
		event(trip, "PICKUP", "arrive", PLANNED);
		event(trip, "PICKUP", "depart", PLANNED.plusSeconds(3600));
		event(trip, "DROPOFF", "arrive", PLANNED.plusSeconds(5 * 3600));
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/complete").header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		String today = KoreanDate.of(Instant.now()).toString();
		mockMvc.perform(get("/api/ledger").header("Authorization", bearer).param("from", today).param("to", today))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].extraIncomes.length()").value(0));
	}

	@Test
	void 받음을_표시하면_미수금에서_빠지고_대기비도_따로_받을_수_있다() throws Exception {
		Trip trip = startedTrip();
		event(trip, "PICKUP", "arrive", PLANNED.minusSeconds(3 * 3600));
		event(trip, "PICKUP", "work-start", PLANNED.plusSeconds(150 * 60));
		event(trip, "PICKUP", "depart", PLANNED.plusSeconds(190 * 60));
		event(trip, "DROPOFF", "arrive", PLANNED.plusSeconds(6 * 3600));
		event(trip, "DROPOFF", "depart", PLANNED.plusSeconds(7 * 3600));

		String receivables = mockMvc.perform(get("/api/ledger/receivables").header("Authorization", bearer))
				.andExpect(jsonPath("$.fareTotal").value(450000))
				.andExpect(jsonPath("$.overdueFareTotal").value(450000))
				.andExpect(jsonPath("$.waitFeeTotal").value(10000))
				.andReturn().getResponse().getContentAsString();
		Integer entryId = JsonPath.read(receivables, "$.entries[0].id");
		Integer incomeId = JsonPath.read(receivables, "$.entries[0].extraIncomes[0].id");

		mockMvc.perform(post("/api/ledger/" + entryId + "/paid").header("Authorization", bearer))
				.andExpect(jsonPath("$.paid").value(true))
				.andExpect(jsonPath("$.overdue").value(false));
		mockMvc.perform(post("/api/ledger/extra-incomes/" + incomeId + "/paid").header("Authorization", bearer))
				.andExpect(jsonPath("$.paid").value(true));
		mockMvc.perform(get("/api/ledger/receivables").header("Authorization", bearer))
				.andExpect(jsonPath("$.fareTotal").value(0))
				.andExpect(jsonPath("$.waitFeeTotal").value(0))
				.andExpect(jsonPath("$.entries.length()").value(0));

		mockMvc.perform(delete("/api/ledger/" + entryId + "/paid").header("Authorization", bearer))
				.andExpect(jsonPath("$.paid").value(false));
	}

	@Test
	void 직접_추가한_줄은_고치고_지울_수_있지만_자동_줄은_지울_수_없다() throws Exception {
		Integer manualId = JsonPath.read(mockMvc.perform(post("/api/ledger").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"workDate\":\"2026-01-11\",\"description\":\"전화로 받은 일\",\"fare\":300000,\"paymentType\":\"COLLECT\",\"brokerName\":\"동네물류\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.auto").value(false))
				.andExpect(jsonPath("$.expectedPaymentDate").value("2026-01-11"))
				.andExpect(jsonPath("$.brokerName").value("동네물류"))
				.andReturn().getResponse().getContentAsString(), "$.id");

		mockMvc.perform(patch("/api/ledger/" + manualId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fare\":320000,\"paymentType\":\"RECEIPT\",\"expectedPaymentDate\":\"2026-02-11\"}"))
				.andExpect(jsonPath("$.fare").value(320000))
				.andExpect(jsonPath("$.paymentTypeLabel").value("인수증"));
		mockMvc.perform(delete("/api/ledger/" + manualId).header("Authorization", bearer))
				.andExpect(status().isNoContent());

		Trip trip = startedTrip();
		event(trip, "PICKUP", "arrive", PLANNED);
		event(trip, "PICKUP", "depart", PLANNED.plusSeconds(3600));
		event(trip, "DROPOFF", "arrive", PLANNED.plusSeconds(5 * 3600));
		event(trip, "DROPOFF", "depart", PLANNED.plusSeconds(6 * 3600));
		Integer autoId = JsonPath.read(mockMvc.perform(put("/api/trips/" + trip.getId() + "/distance").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"distanceKm\":312}"))
				.andExpect(jsonPath("$.distanceKm").value(312))
				.andReturn().getResponse().getContentAsString(), "$.id");
		mockMvc.perform(delete("/api/ledger/" + autoId).header("Authorization", bearer))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LEDGER_409_AUTO"));
	}

	@Test
	void 하루_요약은_그날_장부와_경비와_대기로_계산한다() throws Exception {
		Trip trip = startedTrip();
		event(trip, "PICKUP", "arrive", PLANNED.minusSeconds(3 * 3600));
		event(trip, "PICKUP", "work-start", PLANNED.plusSeconds(150 * 60));
		event(trip, "PICKUP", "depart", PLANNED.plusSeconds(190 * 60));
		event(trip, "DROPOFF", "arrive", PLANNED.plusSeconds(6 * 3600));
		event(trip, "DROPOFF", "depart", PLANNED.plusSeconds(7 * 3600));
		addExpense("{\"spentOn\":\"" + DAY + "\",\"category\":\"FUEL\",\"amount\":150000}").andExpect(status().isCreated());
		addExpense("{\"spentOn\":\"" + DAY + "\",\"category\":\"MEAL\",\"amount\":9000,\"memo\":\"점심\"}");

		mockMvc.perform(get("/api/ledger/daily").header("Authorization", bearer).param("date", DAY))
				.andExpect(jsonPath("$.tripCount").value(1))
				.andExpect(jsonPath("$.fareTotal").value(450000))
				.andExpect(jsonPath("$.waitFeeTotal").value(10000))
				.andExpect(jsonPath("$.expenseTotal").value(159000))
				.andExpect(jsonPath("$.netProfit").value(301000))
				.andExpect(jsonPath("$.waitMinutes").value(330))
				.andExpect(jsonPath("$.claimableWaitFee").value(10000));
	}

	@Test
	void 오늘_남긴_경비는_진행_중인_운행에_붙고_남의_경비는_404() throws Exception {
		Trip trip = startedTrip();

		Integer expenseId = JsonPath.read(addExpense("{\"category\":\"TOLL\",\"amount\":23000}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tripId").value(trip.getId()))
				.andExpect(jsonPath("$.categoryLabel").value("통행료"))
				.andExpect(jsonPath("$.spentOn").value(KoreanDate.of(Instant.now()).toString()))
				.andReturn().getResponse().getContentAsString(), "$.id");
		addExpense("{\"spentOn\":\"2026-01-01\",\"category\":\"MEAL\",\"amount\":9000}")
				.andExpect(jsonPath("$.tripId").doesNotExist());
		addExpense("{\"category\":\"FUEL\",\"amount\":0}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("LEDGER_400"));

		mockMvc.perform(get("/api/expenses").header("Authorization", bearer))
				.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(patch("/api/expenses/" + expenseId).header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"spentOn\":\"" + KoreanDate.of(Instant.now()) + "\",\"category\":\"TOLL\",\"amount\":25000}"))
				.andExpect(jsonPath("$.amount").value(25000));

		User other = userRepository.save(User.create("01090001702", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		mockMvc.perform(delete("/api/expenses/" + expenseId)
						.header("Authorization", "Bearer " + jwtProvider.issue(other.getId()).accessToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EXPENSE_404"));
		mockMvc.perform(delete("/api/expenses/" + expenseId).header("Authorization", bearer))
				.andExpect(status().isNoContent());
	}

	private Trip startedTrip() {
		Trip trip = Trip.create(driver, vehicle, broker, null, 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pyeongtaek, PLANNED, daegu, null);
		trip.start(PLANNED.minusSeconds(4 * 3600));
		return tripRepository.save(trip);
	}

	private void event(Trip trip, String stopType, String action, Instant at) throws Exception {
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/" + stopType + "/wait/" + action)
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"at\":\"" + at + "\"}"))
				.andExpect(status().is2xxSuccessful());
	}

	private ResultActions addExpense(String json) throws Exception {
		return mockMvc.perform(post("/api/expenses").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
