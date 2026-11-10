package com.ieo.server.domain.vehicle.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
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

/** 차량에서 로그인한 뒤 Pleos 트럭(VIN)을 내 차량과 연결 (VEH-004) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VehiclePleosTest {

	private static final String VIN = "KMFZCZ7KBPU123456";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private VehicleRepository vehicleRepository;

	private MockMvc mockMvc;
	private String bearer;
	private Long userId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		bearer = bearerOfNewDriver("01090000601");
		userId = userRepository.findByPhone("01090000601").orElseThrow().getId();
	}

	@Test
	void 차량이_없으면_등록하라고_한다() throws Exception {
		resolve(bearer, VIN)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REGISTER"))
				.andExpect(jsonPath("$.vehicle").doesNotExist());
	}

	@Test
	void 차량이_한_대면_묻지_않고_연결하고_다음부터는_바로_찾는다() throws Exception {
		register(bearer, "경기82사1234", null);

		resolve(bearer, VIN)
				.andExpect(jsonPath("$.status").value("LINKED"))
				.andExpect(jsonPath("$.autoLinked").value(true))
				.andExpect(jsonPath("$.vehicle.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.vehicle.pleosLinked").value(true));

		// 소문자·띄어쓰기가 섞여도 같은 VIN
		resolve(bearer, "kmfz cz7kbpu123456")
				.andExpect(jsonPath("$.status").value("LINKED"))
				.andExpect(jsonPath("$.autoLinked").value(false));
	}

	@Test
	void 차대번호_형식이_아니면_연결하지_않는다() throws Exception {
		register(bearer, "경기82사1234", null);

		// 차대번호를 모르는 차(에뮬레이터)는 "invalid"를 준다
		resolve(bearer, "invalid")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VEHICLE_400_VIN"));
	}

	@Test
	void 예전에_여러_대를_등록한_차주면_고르게_하고_고른_차량이_기본이_된다() throws Exception {
		register(bearer, "경기82사1234", null);
		Integer secondId = legacyVehicle("123바4567");

		resolve(bearer, VIN)
				.andExpect(jsonPath("$.status").value("SELECT"))
				.andExpect(jsonPath("$.candidates.length()").value(2));

		link(bearer, secondId, VIN)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pleosLinked").value(true))
				.andExpect(jsonPath("$.defaultVehicle").value(true));
		resolve(bearer, VIN)
				.andExpect(jsonPath("$.status").value("LINKED"))
				.andExpect(jsonPath("$.vehicle.id").value(secondId));
		mockMvc.perform(get("/api/vehicles").header("Authorization", bearer))
				.andExpect(jsonPath("$[0].defaultVehicle").value(false))
				.andExpect(jsonPath("$[1].defaultVehicle").value(true));
	}

	@Test
	void 차량_앱에서_등록하면서_바로_연결한다() throws Exception {
		register(bearer, "123바4567", VIN)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pleosLinked").value(true))
				.andExpect(jsonPath("$.defaultVehicle").value(true));
	}

	@Test
	void 예전에_여러_대를_등록한_차주는_같은_트럭을_다른_차량으로_옮겨_연결할_수_있다() throws Exception {
		Integer firstId = idOf(register(bearer, "경기82사1234", VIN));
		Integer secondId = legacyVehicle("123바4567");

		link(bearer, secondId, VIN).andExpect(status().isOk());

		mockMvc.perform(get("/api/vehicles").header("Authorization", bearer))
				.andExpect(jsonPath("$[0].id").value(firstId))
				.andExpect(jsonPath("$[0].pleosLinked").value(false))
				.andExpect(jsonPath("$[1].pleosLinked").value(true));
	}

	@Test
	void 다른_차주_차량에_연결된_트럭이면_안내만_하고_연결하지_않는다() throws Exception {
		String other = bearerOfNewDriver("01090000602");
		register(other, "경기82사1234", VIN);
		Integer mineId = idOf(register(bearer, "123바4567", null));

		resolve(bearer, VIN)
				.andExpect(jsonPath("$.status").value("OTHER_OWNER"))
				.andExpect(jsonPath("$.vehicle.id").value(mineId))
				.andExpect(jsonPath("$.vehicle.pleosLinked").value(false));
		link(bearer, mineId, VIN)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_409_VIN"));
		register(bearer, "12가3456", VIN)
				.andExpect(status().isConflict());
	}

	private String bearerOfNewDriver(String phone) {
		User user = userRepository.save(User.create(phone, UserRole.DRIVER));
		driverRepository.save(Driver.of(user));
		return "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	private ResultActions resolve(String bearer, String vin) throws Exception {
		return mockMvc.perform(post("/api/vehicles/pleos/resolve").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vin\":\"" + vin + "\"}"));
	}

	private ResultActions link(String bearer, Integer vehicleId, String vin) throws Exception {
		return mockMvc.perform(post("/api/vehicles/" + vehicleId + "/pleos").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vin\":\"" + vin + "\"}"));
	}

	private ResultActions register(String bearer, String plate, String vin) throws Exception {
		String vinJson = vin == null ? "null" : "\"" + vin + "\"";
		return mockMvc.perform(post("/api/vehicles").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"plateNumber\":\"%s\",\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"vin\":%s}"
						.formatted(plate, vinJson)));
	}

	/**
	 * 차량 1대 규칙(VEH-001) 전에 두 대 이상 등록한 차주. 지금은 API로 못 만들어 저장소에 바로 넣는다.
	 * 그런 차주가 남아 있어도 Pleos 연결은 고르는 흐름으로 동작해야 한다
	 */
	private Integer legacyVehicle(String plate) {
		Driver driver = driverRepository.findById(userId).orElseThrow();
		return Math.toIntExact(vehicleRepository.save(Vehicle.create(driver, plate, Tonnage.TON_5, BodyType.CARGO, null)).getId());
	}

	private static Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
