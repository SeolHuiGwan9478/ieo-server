package com.ieo.server.domain.vehicle.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.repository.DriverDocumentRepository;
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

	@Autowired
	private DriverDocumentRepository documentRepository;

	private MockMvc mockMvc;
	private String driverBearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		driverBearer = bearerOfNewDriver("01090000501");
	}

	@Test
	void 차량은_한_대만_등록한다() throws Exception {
		register(driverBearer, "경기 82사 1234", "TON_5", "CARGO", "3.6")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.tonnageLabel").value("5톤"))
				.andExpect(jsonPath("$.bodyTypeLabel").value("카고"))
				.andExpect(jsonPath("$.heightM").value(3.6))
				.andExpect(jsonPath("$.pleosLinked").value(false))
				.andExpect(jsonPath("$.defaultVehicle").value(true));
		register(driverBearer, "123바4567", "TON_11", "WING", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_409_LIMIT"));

		mockMvc.perform(get("/api/vehicles").header("Authorization", driverBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 차량을_지우면_다시_등록할_수_있다() throws Exception {
		Integer id = idOf(register(driverBearer, "경기82사1234", "TON_5", "CARGO", null));
		mockMvc.perform(delete("/api/vehicles/" + id).header("Authorization", driverBearer))
				.andExpect(status().isNoContent());
		register(driverBearer, "123바4567", "TON_11", "WING", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.defaultVehicle").value(true));
	}

	@Test
	void 번호_톤수_차종을_바꾸면_자동차등록증을_다시_받고_제원만_바꾸면_그대로다() throws Exception {
		User user = userRepository.save(User.create("01090000511", UserRole.DRIVER));
		Driver driver = driverRepository.save(Driver.of(user));
		String bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		Integer id = idOf(register(bearer, "80가4822", "TON_5", "CARGO", "3.6"));
		DriverDocument approved = DriverDocument.submit(driver, DocumentType.VEHICLE_REGISTRATION, "documents/test", "image/png", null);
		approved.approve(1L, java.time.Instant.now());
		documentRepository.save(approved);

		// 제원(높이·폭)만 바꾸면 승인 그대로
		patchVehicle(bearer, id, "{\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"heightM\":3.7,\"widthCm\":249}")
				.andExpect(status().isOk());
		documents(bearer).andExpect(jsonPath(VEHICLE_DOC + ".latest.status").value("APPROVED"));

		// 번호를 바꾸면 자동차등록증이 반려되고 다시 올리라고 안내한다
		patchVehicle(bearer, id, "{\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"heightM\":3.7,\"plateNumber\":\"80가 4823\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plateNumber").value("80가4823"));
		documents(bearer)
				.andExpect(jsonPath("$.verified").value(false))
				.andExpect(jsonPath(VEHICLE_DOC + ".latest.status").value("REJECTED"))
				.andExpect(jsonPath(VEHICLE_DOC + ".latest.rejectReason").value("VEHICLE_CHANGED"));

		// 남이 쓰는 번호로는 못 바꾼다
		register(bearerOfNewDriver("01090000512"), "80가4824", "TON_5", "CARGO", null);
		patchVehicle(bearer, id, "{\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"plateNumber\":\"80가4824\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_409_PLATE"));
	}

	private static final String VEHICLE_DOC = "$.documents[?(@.type == 'VEHICLE_REGISTRATION')]";

	private ResultActions documents(String bearer) throws Exception {
		return mockMvc.perform(get("/api/drivers/me/documents").header("Authorization", bearer));
	}

	private ResultActions patchVehicle(String bearer, Integer id, String json) throws Exception {
		return mockMvc.perform(patch("/api/vehicles/" + id).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json));
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
	void 폭_길이_총중량은_선택이고_범위를_벗어나면_거절한다() throws Exception {
		String bearer = bearerOfNewDriver("01090005551");
		Integer id = idOf(register(bearer, "경기82사7777", "TON_5", "CARGO", "3.6"));

		mockMvc.perform(patch("/api/vehicles/" + id).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"widthCm\":249,\"lengthCm\":850,\"totalWeightKg\":11000,\"axleCount\":3}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.widthCm").value(249))
				.andExpect(jsonPath("$.totalWeightKg").value(11000))
				.andExpect(jsonPath("$.axleCount").value(3));
		mockMvc.perform(patch("/api/vehicles/" + id).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tonnage\":\"TON_5\",\"bodyType\":\"CARGO\",\"widthCm\":999}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VEHICLE_400_DIMENSION"));
	}

	@Test
	void Pleos_연비를_올리면_km_L로_저장하고_이상한_값은_무시한다() throws Exception {
		Integer id = idOf(register(driverBearer, "경기82사1234", "TON_5", "CARGO", null));

		putFuelEconomy(driverBearer, id, "26.3")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.measuredKmPerLiter").value(3.8));
		putFuelEconomy(driverBearer, id, "0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.measuredKmPerLiter").value(3.8));
		putFuelEconomy(bearerOfNewDriver("01090000505"), id, "20")
				.andExpect(status().isNotFound());
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

	private ResultActions putFuelEconomy(String bearer, Integer vehicleId, String litersPer100Km) throws Exception {
		return mockMvc.perform(put("/api/vehicles/" + vehicleId + "/fuel-economy").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"litersPer100Km\":" + litersPer100Km + "}"));
	}

	private static Integer idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
