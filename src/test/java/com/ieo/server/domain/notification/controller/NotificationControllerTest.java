package com.ieo.server.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.notification.dto.NotificationResponse;
import com.ieo.server.domain.notification.enums.NotificationType;
import com.ieo.server.domain.notification.service.NotificationHub;
import com.ieo.server.domain.notification.service.NotificationService;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationControllerTest {

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
	private BrokerMemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationHub hub;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	private MockMvc mockMvc;
	private String broker;
	private Long driverId;
	private String driver;
	private Site pyeongtaek;
	private int phoneSeq = 10;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		Broker hanbit = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		User brokerUser = userRepository.save(User.create(nextPhone(), UserRole.BROKER));
		memberRepository.save(BrokerMember.of(brokerUser, hanbit, BrokerMemberPosition.STAFF));
		broker = bearer(brokerUser.getId());
		driverId = newDriver();
		driver = bearer(driverId);
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시 포승읍", 36.98, 126.84, null));
	}

	@Test
	void 주선사가_확정하면_요청한_차주에게_확정_다른_요청자에게_마감을_알린다() throws Exception {
		Integer orderId = registerOrder("SELECT");
		Integer mine = requestDispatch(driver, orderId);
		String other = bearer(newDriver());
		requestDispatch(other, orderId);
		notifications(driver).andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(post("/api/broker/orders/" + orderId + "/requests/" + mine + "/confirm").header("Authorization", broker))
				.andExpect(status().isOk());

		notifications(driver)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("DISPATCH_CONFIRMED"))
				.andExpect(jsonPath("$[0].dispatchRequestId").value(mine))
				.andExpect(jsonPath("$[0].orderId").value(orderId))
				.andExpect(jsonPath("$[0].message").value(
						"한빛로지스에서 배차를 확정했어요. 평택 포승 물류센터에서 대구 성서 공단까지, 내 배차에 넣었어요"));
		notifications(other)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("DISPATCH_DECLINED"))
				.andExpect(jsonPath("$[0].message").value("평택 포승 물류센터에서 대구 성서 공단까지 가는 배차는 마감됐어요"));
	}

	@Test
	void 자동_확정_오더도_확정_알림이_간다() throws Exception {
		requestDispatch(driver, registerOrder("FIRST"));

		notifications(driver).andExpect(jsonPath("$[0].type").value("DISPATCH_CONFIRMED"));
	}

	@Test
	void 오더를_내리면_기다리던_요청에만_마감을_알리고_이미_만료된_요청에는_만료만_알린다() throws Exception {
		Integer orderId = registerOrder("SELECT");
		Integer mine = requestDispatch(driver, orderId);
		String late = bearer(newDriver());
		requestDispatch(late, orderId);
		agoMinutes(mine, 11);

		mockMvc.perform(post("/api/broker/orders/" + orderId + "/close").header("Authorization", broker))
				.andExpect(status().isOk());

		notifications(late).andExpect(jsonPath("$[0].type").value("DISPATCH_DECLINED"));
		notifications(driver).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 십분_안에_확정되지_않으면_만료를_한_번만_알리고_after_다음부터_이어_받는다() throws Exception {
		Integer first = requestDispatch(driver, registerOrder("SELECT"));
		Integer second = requestDispatch(driver, registerOrder("SELECT"));

		notificationService.expire(first.longValue());
		notifications(driver).andExpect(jsonPath("$.length()").value(0));

		agoMinutes(first, 10);
		notificationService.expire(first.longValue());
		notificationService.expire(first.longValue());
		String body = notifications(driver)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("DISPATCH_EXPIRED"))
				.andExpect(jsonPath("$[0].dispatchRequestId").value(first))
				.andReturn().getResponse().getContentAsString();
		Integer lastId = JsonPath.read(body, "$[0].id");

		// 서버가 다시 켜져 타이머를 잃어도 앱이 다시 붙을 때 채운다
		agoMinutes(second, 12);
		mockMvc.perform(get("/api/notifications").param("after", String.valueOf(lastId)).header("Authorization", driver))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispatchRequestId").value(second))
				.andExpect(jsonPath("$[0].message").value("평택 포승 물류센터에서 대구 성서 공단까지 가는 배차 요청이 10분 안에 확정되지 않았어요"));
	}

	@Test
	void 스트림에_붙으면_놓친_알림부터_받고_새_알림을_바로_받는다() throws Exception {
		Integer first = requestDispatch(driver, registerOrder("SELECT"));
		agoMinutes(first, 10);
		Integer firstId = JsonPath.read(notifications(driver).andReturn().getResponse().getContentAsString(), "$[0].id");
		requestDispatch(driver, registerOrder("FIRST"));

		MvcResult stream = mockMvc.perform(get("/api/notifications/stream")
						.header("Authorization", driver).header("Last-Event-ID", String.valueOf(firstId)))
				.andExpect(request().asyncStarted())
				.andReturn();

		String received = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(received).contains(":connected").contains("event:notification").contains("DISPATCH_CONFIRMED")
				.doesNotContain("DISPATCH_EXPIRED");
		assertThat(hub.connections(driverId)).isEqualTo(1);

		hub.push(driverId, new NotificationResponse(999L, NotificationType.DISPATCH_DECLINED, "마감", 1L, 1L, Instant.now()));
		assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("id:999").contains("\"message\":\"마감\"");
	}

	@Test
	void 로그인하지_않으면_스트림에_붙을_수_없다() throws Exception {
		mockMvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());
	}

	/** 요청 시각을 몇 분 전으로 옮긴다 */
	private void agoMinutes(Integer requestId, int minutes) {
		em.flush();
		jdbc.update("update dispatch_requests set created_at = ? where id = ?",
				java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(minutes))), requestId);
		em.clear();
	}

	private ResultActions notifications(String bearer) throws Exception {
		return mockMvc.perform(get("/api/notifications").header("Authorization", bearer)).andExpect(status().isOk());
	}

	private Integer registerOrder(String confirmMode) throws Exception {
		Instant pickupFrom = Instant.now().plus(Duration.ofDays(1));
		String json = """
				{"pickup": {"siteId": %d},
				 "dropoff": {"site": {"name": "대구 성서 공단", "address": "대구 달서구", "lat": 35.84, "lng": 128.49}},
				 "pickupFrom": "%s", "pickupUntil": "%s", "dropoffBy": "%s",
				 "cargoCategory": "GENERAL", "cargoType": "음료 파레트",
				 "weightKg": 4500, "tonnage": "TON_5", "bodyType": "WING", "loadingMethod": "FORKLIFT",
				 "fare": 450000, "paymentType": "RECEIPT", "expectedPaymentDays": 15,
				 "consignmentType": "DIRECT", "confirmMode": "%s", "targetDriverId": null}
				""".formatted(pyeongtaek.getId(), pickupFrom, pickupFrom.plus(Duration.ofHours(2)),
				pickupFrom.plus(Duration.ofHours(8)), confirmMode);
		return idOf(mockMvc.perform(post("/api/broker/orders").header("Authorization", broker)
				.contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()));
	}

	private Integer requestDispatch(String bearer, Integer orderId) throws Exception {
		return idOf(mockMvc.perform(post("/api/orders/" + orderId + "/requests").header("Authorization", bearer))
				.andExpect(status().isCreated()));
	}

	private Long newDriver() {
		User user = userRepository.save(User.create(nextPhone(), UserRole.DRIVER));
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + (2000 + phoneSeq), Tonnage.TON_11, BodyType.WING, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return user.getId();
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	private String nextPhone() {
		return "010900017" + String.format("%02d", phoneSeq++);
	}

	private Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
