package com.ieo.server.domain.vehicle.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VehicleControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String driverBearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		driverBearer = bearerOfNewDriver("01090000501");
	}

	@Test
	void 첫_차량은_기본_차량이_되고_두_번째부터는_아니다() throws Exception {
		register(driverBearer, "경기 82사 1234", "TON_5", "CARGO", "3.6")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.tonnageLabel").value("5톤"))
				.andExpect(jsonPath("$.bodyTypeLabel").value("카고"))
				.andExpect(jsonPath("$.heightM").value(3.6))
				.andExpect(jsonPath("$.pleosLinked").value(false))
				.andExpect(jsonPath("$.defaultVehicle").value(true));
		register(driverBearer, "123바4567", "TON_11", "WING", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.defaultVehicle").value(false));

		mockMvc.perform(get("/api/vehicles").header("Authorization", driverBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$[1].plateNumber").value("123바4567"));
	}

	@Test
	void 기본_차량을_바꾸면_예전_기본_차량은_풀린다() throws Exception {
		register(driverBearer, "경기82사1234", "TON_5", "CARGO", null);
		Integer secondId = idOf(register(driverBearer, "123바4567", "TON_11", "WING", null));

		mockMvc.perform(post("/api/vehicles/" + secondId + "/default").header("Authorization", driverBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.defaultVehicle").value(true));

		mockMvc.perform(get("/api/vehicles").header("Authorization", driverBearer))
				.andExpect(jsonPath("$[0].defaultVehicle").value(false))
				.andExpect(jsonPath("$[1].defaultVehicle").value(true));
	}

	@Test
	void 기본_차량을_지우면_남은_차량이_기본이_된다() throws Exception {
		Integer firstId = idOf(register(driverBearer, "경기82사1234", "TON_5", "CARGO", null));
		register(driverBearer, "123바4567", "TON_11", "WING", null);

		mockMvc.perform(delete("/api/vehicles/" + firstId).header("Authorization", driverBearer))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/vehicles").header("Authorization", driverBearer))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].plateNumber").value("123바4567"))
				.andExpect(jsonPath("$[0].defaultVehicle").value(true));
	}

	@Test
	void 톤수_차종_높이를_고친다() throws Exception {
		Integer id = idOf(register(driverBearer, "경기82사1234", "TON_5", "CARGO", "3.6"));

		mockMvc.perform(patch("/api/vehicles/" + id).header("Authorization", driverBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tonnage\":\"TON_11\",\"bodyType\":\"WING\",\"heightM\":null}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tonnage").value("TON_11"))
				.andExpect(jsonPath("$.bodyType").value("WING"))
				.andExpect(jsonPath("$.heightM").doesNotExist());
	}

	@Test
	void 다른_차주가_쓰는_번호는_409() throws Exception {
		register(bearerOfNewDriver("01090000502"), "경기82사1234", "TON_5", "CARGO", null);

		register(driverBearer, "경기 82사 1234", "TON_5", "CARGO", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_409_PLATE"));
	}

	@Test
	void 남의_차량은_없는_것처럼_404() throws Exception {
		Integer othersId = idOf(register(bearerOfNewDriver("01090000503"), "경기82사1234", "TON_5", "CARGO", null));

		mockMvc.perform(delete("/api/vehicles/" + othersId).header("Authorization", driverBearer))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VEHICLE_404"));
		mockMvc.perform(post("/api/vehicles/" + othersId + "/default").header("Authorization", driverBearer))
				.andExpect(status().isNotFound());
	}

	@Test
	void 입력값이_틀리면_400() throws Exception {
		register(driverBearer, "ABC1234", "TON_5", "CARGO", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VEHICLE_400_PLATE"));
		register(driverBearer, "경기82사1234", "TON_5", "CARGO", "4.5")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VEHICLE_400_HEIGHT"));
		register(driverBearer, "경기82사1234", "TON_99", "CARGO", null)
				.andExpect(status().isBadRequest());
	}

	@Test
	void 주선사_담당자는_차량을_등록할_수_없다() throws Exception {
		User broker = userRepository.save(User.create("01090000504", UserRole.BROKER));
		String brokerBearer = "Bearer " + jwtProvider.issue(broker.getId()).accessToken();

		register(brokerBearer, "경기82사1234", "TON_5", "CARGO", null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("VEHICLE_403_ROLE"));
	}

	@Test
	void 토큰이_없으면_401() throws Exception {
		mockMvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
	}

	private String bearerOfNewDriver(String phone) {
		User user = userRepository.save(User.create(phone, UserRole.DRIVER));
		driverRepository.save(Driver.of(user));
		return "Bearer " + jwtProvider.issue(user.getId()).accessToken();
	}

	private ResultActions register(String bearer, String plate, String tonnage, String bodyType, String height) throws Exception {
		String heightJson = height == null ? "null" : height;
		return mockMvc.perform(post("/api/vehicles").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"plateNumber\":\"%s\",\"tonnage\":\"%s\",\"bodyType\":\"%s\",\"heightM\":%s}"
						.formatted(plate, tonnage, bodyType, heightJson)));
	}

	private static Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
