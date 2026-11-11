package com.ieo.server.domain.trip.controller;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.global.sms.SmsSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripPosition;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripPositionRepository;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.auth.JwtProvider;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class TripLocationControllerTest {

	@MockitoBean
	private SmsSender smsSender;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository brokerMemberRepository;

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
	private TripPositionRepository positionRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private EntityManager em;

	@Autowired
	private Clock clock;


	private MockMvc mockMvc;
	private String bearer;
	private Trip trip;
	private Instant now;

	@Autowired
	private UserConsentRepository consentRepository;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		// DB는 마이크로초까지 저장한다. Windows 시계(100ns)에서도 같은 값을 비교하도록 자른다
		now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		User user = userRepository.save(User.create("01090000901", UserRole.DRIVER));
		Driver driver = driverRepository.save(Driver.of(user));
		Vehicle vehicle = vehicleRepository.save(
				Vehicle.create(driver, "경기82사5678", Tonnage.TON_5, BodyType.CARGO, null));
		Site pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		Site dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
		trip = tripRepository.save(Trip.create(driver, vehicle, null, "한빛로지스", 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, pickup, now, dropoff, now.plusSeconds(36_000)));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	@Test
	void 묶음에서_가장_늦게_잰_위치만_남기고_같은_묶음을_다시_보내도_같다() throws Exception {
		trip.start(now);
		String body = points(point(now.minusSeconds(60), 36.90, 127.00, 210_000, 9_000),
				point(now.minusSeconds(30), 36.95, 126.95, 200_000, 8_600));

		send(body).andExpect(status().isNoContent());
		send(body).andExpect(status().isNoContent());

		TripPosition position = position();
		assertThat(position.getRecordedAt()).isEqualTo(now.minusSeconds(30));
		assertThat(position.getLat()).isEqualTo(36.95);
		assertThat(position.getRemainMeters()).isEqualTo(200_000);
		assertThat(position.getRouteMeters()).isEqualTo(200_000);
	}

	@Test
	void 운행이_끝나면_위치를_지우고_늦게_온_위치는_버린다() throws Exception {
		trip.start(now);
		send(points(point(now, 36.90, 127.00, 210_000, 9_000)));
		em.flush();

		mockMvc.perform(post("/api/trips/{id}/cancel", trip.getId()).header("Authorization", bearer))
				.andExpect(status().isOk());
		send(points(point(now.plusSeconds(30), 36.91, 127.01, 200_000, 8_600))).andExpect(status().isNoContent());

		em.flush();
		em.clear();
		assertThat(positionRepository.findById(trip.getId())).isEmpty();
	}

	@Test
	void 좌표가_틀리거나_남의_운행이면_거부한다() throws Exception {
		trip.start(now);
		send(points(point(now, 91.0, 127.0, null, null))).andExpect(status().isBadRequest());
		send("{\"points\": []}").andExpect(status().isBadRequest());

		User other = userRepository.save(User.create("01090000902", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		mockMvc.perform(post("/api/trips/{id}/locations", trip.getId())
						.header("Authorization", "Bearer " + jwtProvider.issue(other.getId()).accessToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(points(point(now, 36.9, 127.0, null, null))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRIP_404"));
	}

	@Test
	void 주선사에게_출발_50퍼센트_도착_30분_전에_도착_시각을_알린다() throws Exception {
		Broker broker = brokerRepository.save(Broker.create("대한운송", "1234567891", "제2026-002호", null));
		User owner = userRepository.save(User.create("01090001777", UserRole.BROKER));
		brokerMemberRepository.save(BrokerMember.of(owner, broker, BrokerMemberPosition.OWNER));
		trip = tripRepository.save(Trip.create(trip.getDriver(), trip.getVehicle(), broker, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, trip.pickup().getSite(), now, trip.dropoff().getSite(), now.plusSeconds(36_000)));
		consentRepository.save(UserConsent.of(trip.getDriver().getUser(), ConsentType.BROKER_SHARE, "2026-11", true));
		toInTransit();

		send(points(point(now.minusSeconds(150), 36.9, 126.9, 300_000, 12_000)));
		send(points(point(now.minusSeconds(120), 36.7, 127.2, 200_000, 8_000)));
		send(points(point(now.minusSeconds(90), 36.5, 127.5, 140_000, 6_000)));
		send(points(point(now.minusSeconds(60), 36.0, 128.2, 30_000, 2_000)));
		send(points(point(now.minusSeconds(30), 35.9, 128.4, 25_000, 1_700)));

		verify(smsSender, times(3)).send(eq("01090001777"), contains("도착해요"));
	}

	@Test
	void 주선사_공유에_동의하지_않으면_도착_시각을_보내지_않는다() throws Exception {
		Broker broker = brokerRepository.save(Broker.create("대한운송", "1234567891", "제2026-002호", null));
		User owner = userRepository.save(User.create("01090001778", UserRole.BROKER));
		brokerMemberRepository.save(BrokerMember.of(owner, broker, BrokerMemberPosition.OWNER));
		trip = tripRepository.save(Trip.create(trip.getDriver(), trip.getVehicle(), broker, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, trip.pickup().getSite(), now, trip.dropoff().getSite(), now.plusSeconds(36_000)));
		toInTransit();

		send(points(point(now.minusSeconds(60), 36.9, 126.9, 300_000, 12_000)));

		verifyNoInteractions(smsSender);
	}

	@Test
	void 이름만_적은_주선사에게는_도착_시각을_보내지_않는다() throws Exception {
		toInTransit();
		send(points(point(now.minusSeconds(60), 36.9, 126.9, 300_000, 12_000)));
		verifyNoInteractions(smsSender);
	}

	private void toInTransit() {
		trip.start(now);
		trip.arriveAtPickup();
		trip.departPickup();
	}

	private TripPosition position() {
		em.flush();
		em.clear();
		return positionRepository.findById(trip.getId()).orElseThrow();
	}

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(post("/api/trips/{id}/locations", trip.getId())
				.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String points(String... points) {
		return "{\"points\": [" + String.join(",", points) + "]}";
	}

	private static String point(Instant at, double lat, double lng, Integer remainMeters, Integer remainSeconds) {
		return "{\"recordedAt\": \"%s\", \"lat\": %s, \"lng\": %s, \"remainMeters\": %s, \"remainSeconds\": %s}"
				.formatted(at, lat, lng, remainMeters, remainSeconds);
	}
}
