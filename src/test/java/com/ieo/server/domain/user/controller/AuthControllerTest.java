package com.ieo.server.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.sms.SmsSender;
import com.jayway.jsonpath.JsonPath;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

	private static final String DEVICE = "device-a";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserConsentRepository userConsentRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@MockitoBean
	private SmsSender smsSender;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void 새_번호는_약관_동의와_기본_정보로_가입한다() throws Exception {
		String phone = "01020000001";
		String verify = verifyPhone(phone);
		assertThat((Boolean) JsonPath.read(verify, "$.accountExists")).isFalse();
		String phoneToken = JsonPath.read(verify, "$.phoneToken");

		String tokens = post("/api/auth/signup", signUpBody(phoneToken, true))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		User user = userRepository.findByPhone(phone).orElseThrow();
		assertThat(user.getName()).isEqualTo("김도윤");
		assertThat(user.getRole()).isEqualTo(UserRole.DRIVER);
		assertThat(driverRepository.existsById(user.getId())).isTrue();
		assertThat(userConsentRepository.findLatestByUserId(user.getId())).hasSize(4);

		// 같은 phoneToken으로 다시 가입할 수 없다
		post("/api/auth/signup", signUpBody(phoneToken, true))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_PHONE"));

		// 리프레시 토큰으로 다시 발급, 액세스 토큰은 리프레시로 못 쓴다
		post("/api/auth/refresh", "{\"refreshToken\":\"" + JsonPath.read(tokens, "$.refreshToken") + "\"}")
				.andExpect(status().isOk());
		post("/api/auth/refresh", "{\"refreshToken\":\"" + JsonPath.read(tokens, "$.accessToken") + "\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_TOKEN"));
	}

	@Test
	void 가입된_번호면_계정_정보를_주고_phoneToken으로_로그인한다() throws Exception {
		String phone = "01020000002";
		User user = User.create(phone, UserRole.DRIVER);
		user.changeName("김도윤");
		userRepository.save(user);

		String verify = verifyPhone(phone);
		assertThat((Boolean) JsonPath.read(verify, "$.accountExists")).isTrue();
		assertThat((String) JsonPath.read(verify, "$.existingAccount.name")).isEqualTo("김도윤");
		assertThat((String) JsonPath.read(verify, "$.existingAccount.role")).isEqualTo("DRIVER");

		String phoneToken = JsonPath.read(verify, "$.phoneToken");
		post("/api/auth/phone/login", "{\"phoneToken\":\"" + phoneToken + "\",\"deviceId\":\"" + DEVICE + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
		assertThat(userRepository.findByPhone(phone).orElseThrow().getLastLoginAt()).isNotNull();

		// 같은 phoneToken으로 두 번 로그인할 수 없다
		post("/api/auth/phone/login", "{\"phoneToken\":\"" + phoneToken + "\",\"deviceId\":\"" + DEVICE + "\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_PHONE"));
	}

	@Test
	void 다른_기기에서는_phoneToken을_쓸_수_없다() throws Exception {
		String phone = "01020000005";
		userRepository.save(User.create(phone, UserRole.DRIVER));
		String phoneToken = JsonPath.read(verifyPhone(phone), "$.phoneToken");

		post("/api/auth/phone/login", "{\"phoneToken\":\"" + phoneToken + "\",\"deviceId\":\"device-b\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_PHONE"));
	}

	@Test
	void 만든_적_없는_phoneToken은_거부한다() throws Exception {
		post("/api/auth/phone/login", "{\"phoneToken\":\"made-up\",\"deviceId\":\"" + DEVICE + "\"}")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 필수_약관이_빠지면_400이고_phoneToken은_그대로_쓸_수_있다() throws Exception {
		String phoneToken = JsonPath.read(verifyPhone("01020000003"), "$.phoneToken");

		post("/api/auth/signup", signUpBody(phoneToken, false))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AUTH_400_CONSENT"));

		post("/api/auth/signup", signUpBody(phoneToken, true))
				.andExpect(status().isCreated());
	}

	@Test
	void 인증번호가_틀리면_남은_횟수를_알려준다() throws Exception {
		String phone = "01020000004";
		post("/api/auth/phone/code", "{\"phone\":\"" + phone + "\"}").andExpect(status().isNoContent());
		String wrong = lastCode().equals("000000") ? "111111" : "000000";

		post("/api/auth/phone/verify", verifyBody(phone, wrong))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AUTH_400_CODE"))
				.andExpect(jsonPath("$.message").value("인증번호가 맞지 않습니다. 4번 더 틀리면 10분 동안 막힙니다."));

		// 틀린 횟수가 DB에 남는다
		post("/api/auth/phone/verify", verifyBody(phone, wrong))
				.andExpect(jsonPath("$.message").value("인증번호가 맞지 않습니다. 3번 더 틀리면 10분 동안 막힙니다."));
	}

	@Test
	void 번호_형식이_틀리면_400() throws Exception {
		post("/api/auth/phone/code", "{\"phone\":\"010-1234-5678\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));
	}

	@Test
	void 차주는_가입하면서_차량을_등록하고_기본_차량이_된다() throws Exception {
		String phoneToken = JsonPath.read(verifyPhone("01020000006"), "$.phoneToken");

		post("/api/auth/signup", signUpBodyWithVehicle(phoneToken, "DRIVER", "경기 82사 1234"))
				.andExpect(status().isCreated());

		User user = userRepository.findByPhone("01020000006").orElseThrow();
		assertThat(vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(user.getId()))
				.get().extracting(Vehicle::getPlateNumber).isEqualTo("경기82사1234");
	}

	@Test
	void 가입할_때_이미_쓰는_차량_번호면_409() throws Exception {
		String first = JsonPath.read(verifyPhone("01020000007"), "$.phoneToken");
		post("/api/auth/signup", signUpBodyWithVehicle(first, "DRIVER", "123바4567")).andExpect(status().isCreated());

		String second = JsonPath.read(verifyPhone("01020000008"), "$.phoneToken");
		post("/api/auth/signup", signUpBodyWithVehicle(second, "DRIVER", "123바4567"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VEHICLE_409_PLATE"));
	}

	@Test
	void 주선사는_가입할_때_차량을_보낼_수_없다() throws Exception {
		String phoneToken = JsonPath.read(verifyPhone("01020000009"), "$.phoneToken");

		post("/api/auth/signup", signUpBodyWithVehicle(phoneToken, "BROKER", "12가3456"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	private static String signUpBodyWithVehicle(String phoneToken, String role, String plateNumber) {
		return """
				{"phoneToken":"%s","deviceId":"%s","role":"%s","name":"김도윤","consents":[
				 {"type":"TERMS","version":"2026-10-01","agreed":true},
				 {"type":"PRIVACY","version":"2026-10-01","agreed":true},
				 {"type":"LOCATION","version":"2026-10-01","agreed":true}],
				 "vehicle":{"plateNumber":"%s","tonnage":"TON_5","bodyType":"CARGO","heightM":3.6}}
				""".formatted(phoneToken, DEVICE, role, plateNumber);
	}

	private String verifyPhone(String phone) throws Exception {
		post("/api/auth/phone/code", "{\"phone\":\"" + phone + "\"}").andExpect(status().isNoContent());
		return post("/api/auth/phone/verify", verifyBody(phone, lastCode()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String lastCode() {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(anyString(), message.capture());
		Matcher matcher = Pattern.compile("\\d{6}").matcher(message.getValue());
		matcher.find();
		return matcher.group();
	}

	private static String verifyBody(String phone, String code) {
		return "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\",\"deviceId\":\"" + DEVICE + "\"}";
	}

	private static String signUpBody(String phoneToken, boolean agreeLocation) {
		return """
				{"phoneToken":"%s","deviceId":"%s","role":"DRIVER","name":"김도윤","consents":[
				 {"type":"TERMS","version":"2026-10-01","agreed":true},
				 {"type":"PRIVACY","version":"2026-10-01","agreed":true},
				 {"type":"LOCATION","version":"2026-10-01","agreed":%s},
				 {"type":"MARKETING","version":"2026-10-01","agreed":false}]}
				""".formatted(phoneToken, DEVICE, agreeLocation);
	}

	private ResultActions post(String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(url)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}
}
