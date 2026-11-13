package com.ieo.server.domain.broker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerDriverControllerTest {

	private static final String DRIVER_PHONE = "01090006001";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private BrokerService brokerService;

	@Autowired
	private BrokerDriverLinkRepository linkRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private User owner;
	private User driver;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090006101", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		driver = driverUser(DRIVER_PHONE, "김기사");
	}

	@Test
	void 주선사가_번호로_요청하고_차주가_수락하면_단골이_된다() throws Exception {
		long linkId = invite(DRIVER_PHONE);

		perform(get("/api/brokers/me/driver-invitations"), owner)
				.andExpect(jsonPath("$[0].phone").value(DRIVER_PHONE))
				.andExpect(jsonPath("$[0].invitedByName").value("김대표"));
		perform(get("/api/driver-links/invitations"), driver)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(linkId))
				.andExpect(jsonPath("$[0].brokerName").value("한빛로지스"))
				.andExpect(jsonPath("$[0].expiresAt").exists());

		perform(post("/api/driver-links/invitations/{id}/accept", linkId), driver)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LINKED"))
				.andExpect(jsonPath("$.linkedAt").exists());

		perform(get("/api/brokers/me/drivers"), owner)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].driverUserId").value(driver.getId()))
				.andExpect(jsonPath("$[0].name").value("김기사"))
				.andExpect(jsonPath("$[0].plateNumber").value("경기82사6001"))
				.andExpect(jsonPath("$[0].tonnage").value("TON_5"));
		perform(get("/api/driver-links"), driver)
				.andExpect(jsonPath("$[0].brokerName").value("한빛로지스"));
		perform(get("/api/brokers/me/driver-invitations"), owner)
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 가입한_차주에게는_단골_요청_앱_알림이_가고_미가입_번호에는_가지_않는다() throws Exception {
		long linkId = invite(DRIVER_PHONE);

		perform(get("/api/notifications"), driver)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("DRIVER_LINK_REQUESTED"))
				.andExpect(jsonPath("$[0].driverLinkId").value(linkId))
				.andExpect(jsonPath("$[0].orderId").doesNotExist())
				.andExpect(jsonPath("$[0].message").value("한빛로지스에서 단골 연결을 요청했어요. 휴대폰 앱에서 수락하거나 거절할 수 있어요"));

		invite("01090006009");
		User newcomer = driverUser("01090006009", "새기사");
		perform(get("/api/notifications"), newcomer)
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 미가입_번호도_요청이_저장되고_가입하면_보인다() throws Exception {
		invite("01090006009");

		User newcomer = driverUser("01090006009", "새기사");
		perform(get("/api/driver-links/invitations"), newcomer)
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 같은_번호로_다시_요청하거나_이미_단골이면_409() throws Exception {
		long linkId = invite(DRIVER_PHONE);
		inviteRequest(DRIVER_PHONE)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_409_DUPLICATE"));

		perform(post("/api/driver-links/invitations/{id}/accept", linkId), driver);
		inviteRequest(DRIVER_PHONE)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_409_LINKED"));
	}

	@Test
	void 차주가_아닌_가입_번호로는_단골_요청을_보낼_수_없다() throws Exception {
		user("01090006009", UserRole.BROKER, "박대표");
		inviteRequest("01090006009")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_400_ROLE"));
	}

	@Test
	void 거절_취소_만료된_요청은_수락할_수_없다() throws Exception {
		long declined = invite(DRIVER_PHONE);
		perform(post("/api/driver-links/invitations/{id}/decline", declined), driver)
				.andExpect(status().isNoContent());
		perform(post("/api/driver-links/invitations/{id}/accept", declined), driver)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_409_CLOSED"));

		long canceled = invite(DRIVER_PHONE);
		perform(post("/api/brokers/me/driver-invitations/{id}/cancel", canceled), owner)
				.andExpect(status().isNoContent());
		perform(post("/api/driver-links/invitations/{id}/accept", canceled), driver)
				.andExpect(status().isConflict());

		long expired = invite(DRIVER_PHONE);
		BrokerDriverLink link = linkRepository.findById(expired).orElseThrow();
		ReflectionTestUtils.setField(link, "createdAt", Instant.now().minus(BrokerDriverLink.TTL));
		perform(get("/api/driver-links/invitations"), driver)
				.andExpect(jsonPath("$.length()").value(0));
		perform(post("/api/driver-links/invitations/{id}/accept", expired), driver)
				.andExpect(status().isConflict());
	}

	@Test
	void 남의_번호로_온_요청은_404이고_주선사_계정은_받을_수_없다() throws Exception {
		long linkId = invite(DRIVER_PHONE);
		User other = driverUser("01090006002", "다른기사");
		perform(post("/api/driver-links/invitations/{id}/accept", linkId), other)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_404"));

		perform(get("/api/driver-links/invitations"), owner)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_403_ROLE"));
	}

	@Test
	void 주선사와_차주_모두_해제할_수_있고_다시_요청할_수_있다() throws Exception {
		long first = link();
		perform(post("/api/driver-links/{id}/unlink", first), driver)
				.andExpect(status().isNoContent());
		perform(get("/api/brokers/me/drivers"), owner)
				.andExpect(jsonPath("$.length()").value(0));
		perform(post("/api/driver-links/{id}/unlink", first), driver)
				.andExpect(status().isNotFound());

		long second = link();
		perform(post("/api/brokers/me/drivers/{id}/unlink", second), owner)
				.andExpect(status().isNoContent());
		perform(get("/api/driver-links"), driver)
				.andExpect(jsonPath("$.length()").value(0));
		perform(post("/api/brokers/me/drivers/{id}/unlink", second), owner)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRIVER_LINK_409_CLOSED"));
	}

	@Test
	void 다른_회사의_연결은_404() throws Exception {
		long linkId = link();
		User rival = user("01090006102", UserRole.BROKER, "박대표");
		brokerService.register(rival.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));

		perform(post("/api/brokers/me/drivers/{id}/unlink", linkId), rival)
				.andExpect(status().isNotFound());
		perform(post("/api/brokers/me/driver-invitations/{id}/cancel", linkId), rival)
				.andExpect(status().isNotFound());
		perform(get("/api/brokers/me/drivers"), rival)
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 차주가_탈퇴하면_요청과_연결이_지워진다() throws Exception {
		link();
		invite("01090006009");
		User leaving = driverUser("01090006009", "떠날기사");

		perform(post("/api/users/me/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"), driver)
				.andExpect(status().isNoContent());
		perform(post("/api/users/me/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"), leaving)
				.andExpect(status().isNoContent());

		assertThat(linkRepository.findAll()).isEmpty();
		perform(get("/api/brokers/me/drivers"), owner)
				.andExpect(jsonPath("$.length()").value(0));
	}

	private long link() throws Exception {
		long linkId = invite(DRIVER_PHONE);
		perform(post("/api/driver-links/invitations/{id}/accept", linkId), driver).andExpect(status().isOk());
		return linkId;
	}

	private long invite(String phone) throws Exception {
		String body = inviteRequest(phone)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions inviteRequest(String phone) throws Exception {
		return perform(post("/api/brokers/me/driver-invitations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"), owner);
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

	private User driverUser(String phone, String name) {
		User user = user(phone, UserRole.DRIVER, name);
		Driver created = driverRepository.save(Driver.of(user));
		Vehicle vehicle = Vehicle.create(created, "경기82사" + phone.substring(7), Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.markDefault();
		vehicleRepository.save(vehicle);
		return user;
	}
}
