package com.ieo.server.domain.wait.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteStats;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.site.repository.SiteStatsRepository;
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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteStatsCalculatorTest {

	@Autowired
	private SiteStatsCalculator calculator;

	@Autowired
	private SiteStatsRepository statsRepository;

	@Autowired
	private WaitRecordRepository recordRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JwtProvider jwtProvider;

	private Driver driver;
	private Vehicle vehicle;
	private Site chilgok;
	private Site daegu;
	private String bearer;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("01090001101", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		chilgok = siteRepository.save(Site.create("칠곡 물류센터", "경북 칠곡군", 35.955, 128.528, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	@Test
	void 최근_90일_기록으로_평균_대기와_작업을_낸다() {
		Instant now = Instant.now();
		visit(now.minus(Duration.ofDays(1)), 90, 30);
		visit(now.minus(Duration.ofDays(2)), 60, 40);
		visit(now.minus(Duration.ofDays(3)), 90, null);   // 작업 시작만 있고 아직 이탈 전
		visit(now.minus(Duration.ofDays(100)), 600, 600); // 90일 밖이라 빠진다

		calculator.recalculate(chilgok);

		SiteStats stats = statsRepository.findById(chilgok.getId()).orElseThrow();
		assertThat(stats.getSampleCount()).isEqualTo(3);
		assertThat(stats.getAvgWaitMinutes()).isEqualTo(80);
		assertThat(stats.getAvgWorkMinutes()).isNull(); // 작업 기록이 2건뿐
		assertThat(stats.hasEnoughSamples()).isTrue();
	}

	@Test
	void 기록이_3건_미만이면_평균을_비우고_정보_카드에_넣지_않는다() throws Exception {
		Instant now = Instant.now();
		visit(now.minus(Duration.ofDays(1)), 90, 30);
		visit(now.minus(Duration.ofDays(2)), 60, 40);
		calculator.recalculate(chilgok);

		assertThat(statsRepository.findById(chilgok.getId()).orElseThrow().getAvgWaitMinutes()).isNull();
		mockMvc().perform(get("/api/sites/" + chilgok.getId()).header("Authorization", bearer))
				.andExpect(jsonPath("$.stats").doesNotExist());

		visit(now.minus(Duration.ofDays(3)), 90, 50);
		calculator.recalculate(chilgok);
		mockMvc().perform(get("/api/sites/" + chilgok.getId()).header("Authorization", bearer))
				.andExpect(jsonPath("$.stats.avgWaitMinutes").value(80))
				.andExpect(jsonPath("$.stats.avgWorkMinutes").value(40))
				.andExpect(jsonPath("$.stats.sampleCount").value(3));
	}

	@Test
	void 상차_시작을_기록하면_그_상하차지_통계가_바로_갱신된다() throws Exception {
		Trip trip = tripRepository.save(Trip.create(driver, vehicle, null, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, chilgok, null, daegu, null));
		trip.start(Instant.now().minusSeconds(3 * 3600));
		Instant arrived = Instant.now().minusSeconds(2 * 3600);

		MockMvc mockMvc = mockMvc();
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/PICKUP/wait/arrive").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"at\":\"" + arrived + "\"}"));
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/stops/PICKUP/wait/work-start").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"at\":\"" + arrived.plusSeconds(75 * 60) + "\"}"));

		SiteStats stats = statsRepository.findById(chilgok.getId()).orElseThrow();
		assertThat(stats.getSampleCount()).isEqualTo(1);
		assertThat(stats.getAvgWaitMinutes()).isNull();
	}

	@Test
	void 새벽_배치는_최근_기록이_있는_상하차지를_모두_계산한다() {
		visit(Instant.now().minus(Duration.ofDays(1)), 90, 30);

		calculator.recalculateAll();

		assertThat(statsRepository.findById(chilgok.getId())).get()
				.extracting(SiteStats::getSampleCount).isEqualTo(1);
	}

	/** 칠곡 상차: arrivedAt 진입, waitMinutes 뒤 작업 시작, workMinutes 뒤 이탈 (null이면 이탈 전) */
	private void visit(Instant arrivedAt, int waitMinutes, Integer workMinutes) {
		Trip trip = tripRepository.save(Trip.create(driver, vehicle, null, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, chilgok, null, daegu, null));
		WaitRecord record = WaitRecord.start(trip.pickup(), arrivedAt, null);
		Instant workStartedAt = arrivedAt.plusSeconds(waitMinutes * 60L);
		record.startWork(workStartedAt);
		if (workMinutes != null) {
			record.depart(workStartedAt.plusSeconds(workMinutes * 60L));
		}
		recordRepository.save(record);
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}
}
