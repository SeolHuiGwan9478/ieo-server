package com.ieo.server.domain.restarea.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.domain.restarea.service.RestAreaSnapshot;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.RouteLine;
import com.ieo.server.domain.restarea.service.TruckRouteLineSource.Shelter;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 운행 중 경로 위 쉴 곳. TMAP 경로는 남쪽으로 곧게 가는 가짜 경로로 바꾼다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripRestAreaControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

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
	private RestAreaRepository restAreaRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private TruckRouteLineSource routeLines;

	private MockMvc mockMvc;
	private Trip trip;
	private String bearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090000811", UserRole.DRIVER));
		Driver driver = driverRepository.save(Driver.of(user));
		Vehicle vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1111", Tonnage.TON_5, BodyType.CARGO, null));
		Site pickup = siteRepository.save(Site.create("청주 물류센터", "충북 청주시", 36.62, 127.40, null));
		Site dropoff = siteRepository.save(Site.create("대전 하차지", "대전 대덕구", 36.30, 127.40, null));
		trip = tripRepository.save(Trip.create(driver, vehicle, null, "한빛로지스", 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, pickup, NOW, dropoff, NOW.plusSeconds(36_000)));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		List<String> lounge = List.of("수유실", "수면실", "샤워실", "세탁실");
		restAreaRepository.save(RestArea.create(new RestAreaSnapshot("A1", "오른쪽(부산)휴게소", "경부선", "부산", "하행", lounge,
				true, "043-000-0001", null, 36.50, 127.399), NOW));
		restAreaRepository.save(RestArea.create(new RestAreaSnapshot("A2", "오른쪽(서울)휴게소", "경부선", "서울", "상행", lounge,
				true, "043-000-0002", null, 36.50, 127.401), NOW));
		restAreaRepository.save(RestArea.create(new RestAreaSnapshot("A3", "샤워만(부산)휴게소", "경부선", "부산", "하행",
				List.of("샤워실"), false, null, null, 36.45, 127.399), NOW));
	}

	private static RouteLine southbound() {
		List<double[]> points = new ArrayList<>();
		for (int i = 0; i <= 11; i++) {
			points.add(new double[] {36.60 - i * 0.027, 127.40});
		}
		return new RouteLine(points, List.of(new Shelter("중간졸음쉼터", 36.40, 127.40)));
	}

	@Test
	void 경로_위_오른쪽_라운지와_졸음쉼터를_경로_거리순으로() throws Exception {
		given(routeLines.route(eq(36.60), eq(127.40), eq(36.62), eq(127.40), any())).willReturn(Optional.of(southbound()));

		mockMvc.perform(get("/api/trips/{id}/rest-areas", trip.getId()).header("Authorization", bearer)
						.param("lat", "36.60").param("lng", "127.40"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routed").value(true))
				.andExpect(jsonPath("$.items[*].name", contains("오른쪽휴게소", "중간졸음쉼터")))
				.andExpect(jsonPath("$.items[0].kind").value("LOUNGE"))
				.andExpect(jsonPath("$.items[0].direction").value("부산"))
				.andExpect(jsonPath("$.items[0].facilities", contains("수유실", "수면실", "샤워실", "세탁실")))
				.andExpect(jsonPath("$.items[1].kind").value("DROWSY_SHELTER"))
				.andExpect(jsonPath("$.attribution").isNotEmpty());
	}

	@Test
	void 출발한_뒤_운송_중이면_하차지까지_경로를_받는다() throws Exception {
		trip.start(NOW);
		trip.arriveAtPickup();
		trip.departPickup();
		given(routeLines.route(anyDouble(), anyDouble(), eq(36.30), eq(127.40), any())).willReturn(Optional.of(southbound()));

		mockMvc.perform(get("/api/trips/{id}/rest-areas", trip.getId()).header("Authorization", bearer)
						.param("lat", "36.60").param("lng", "127.40"))
				.andExpect(jsonPath("$.routed").value(true))
				.andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	void 경로를_못_받으면_routed_false와_빈_목록() throws Exception {
		given(routeLines.route(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any())).willReturn(Optional.empty());

		mockMvc.perform(get("/api/trips/{id}/rest-areas", trip.getId()).header("Authorization", bearer)
						.param("lat", "36.60").param("lng", "127.40"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routed").value(false))
				.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void 남의_운행은_404() throws Exception {
		User other = userRepository.save(User.create("01090000812", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		mockMvc.perform(get("/api/trips/{id}/rest-areas", trip.getId())
						.header("Authorization", "Bearer " + jwtProvider.issue(other.getId()).accessToken())
						.param("lat", "36.60").param("lng", "127.40"))
				.andExpect(status().isNotFound());
	}
}
