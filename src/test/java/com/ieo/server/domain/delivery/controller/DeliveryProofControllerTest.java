package com.ieo.server.domain.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.util.KoreanDate;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryProofControllerTest {

	/** PNG 파일 앞부분(매직 바이트) + 내용 */
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
	private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};
	private static final String SIGNATURE = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);

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
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String bearer;
	private Driver driver;
	private Vehicle vehicle;
	private Site pyeongtaek;
	private Site daegu;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090001801", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		bearer = "Bearer " + jwtProvider.issue(user.getId()).accessToken();
		pyeongtaek = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		daegu = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구 성서로 1", 35.84, 128.49, null));
	}

	@Test
	void 수하인_서명_페이지는_로그인_없이_열린다() throws Exception {
		mockMvc.perform(get("/r/아무토큰"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/receipt.html"));
	}

	@Test
	void 수하인이_링크로_서명하면_운행이_끝나고_지급_예정일이_서명일부터_잡힌다() throws Exception {
		Trip trip = tripAtDropoff();

		String token = tokenOf(issue(trip, "{\"lat\":35.84,\"lng\":128.49}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.path").exists()));
		issue(trip, null).andExpect(status().isOk()).andExpect(jsonPath("$.token").value(token));
		// QR에는 수하인 서명 페이지 주소가 들어간다
		issue(trip, null).andExpect(jsonPath("$.path").value("/r/" + token));

		mockMvc.perform(get("/api/public/delivery-proofs/" + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pickupSiteName").value("평택 포승 물류센터"))
				.andExpect(jsonPath("$.dropoffAddress").value("대구 달서구 성서로 1"))
				.andExpect(jsonPath("$.plateNumber").value("경기82사1234"))
				.andExpect(jsonPath("$.brokerName").value("동네물류"))
				.andExpect(jsonPath("$.expiresAt").doesNotExist());

		sign(token, "{\"signerName\":\"김수하\",\"signature\":\"" + SIGNATURE + "\",\"issueNote\":\"박스 1개 찌그러짐\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIGNED"))
				.andExpect(jsonPath("$.signerName").value("김수하"))
				.andExpect(jsonPath("$.issueNote").value("박스 1개 찌그러짐"))
				.andExpect(jsonPath("$.signaturePath").exists())
				.andExpect(jsonPath("$.expiresAt").exists());

		mockMvc.perform(get("/api/trips/" + trip.getId()).header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
		String today = KoreanDate.of(Instant.now()).toString();
		mockMvc.perform(get("/api/ledger").header("Authorization", bearer).param("from", today).param("to", today))
				.andExpect(jsonPath("$[0].expectedPaymentDate").value(KoreanDate.of(Instant.now()).plusDays(30).toString()));
		mockMvc.perform(get("/api/public/delivery-proofs/" + token + "/signature"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(PNG));
		mockMvc.perform(get("/api/trips/" + trip.getId() + "/delivery-proof").header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value("SIGNED"));
	}

	@Test
	void 두_번_서명하거나_서명이_PNG가_아니면_거부한다() throws Exception {
		String token = tokenOf(issue(tripAtDropoff(), null));

		sign(token, "{\"signature\":\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPG) + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DELIVERY_400_FILE"));
		sign(token, "{\"signature\":\"data:image/png;base64,!!!\"}")
				.andExpect(jsonPath("$.code").value("DELIVERY_400_FILE"));
		sign(token, "{\"signature\":\"" + SIGNATURE + "\"}").andExpect(status().isOk());
		sign(token, "{\"signature\":\"" + SIGNATURE + "\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DELIVERY_409_SIGNED"));
	}

	@Test
	void 수하인은_이상_사항_사진을_올리고_볼_수_있다() throws Exception {
		String token = tokenOf(issue(tripAtDropoff(), null));

		String body = mockMvc.perform(multipart("/api/public/delivery-proofs/" + token + "/photos")
						.file(new MockMultipartFile("file", "damage.jpg", "image/jpeg", JPG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photos.length()").value(1))
				.andExpect(jsonPath("$.photos[0].type").value("ISSUE"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String path = JsonPath.read(body, "$.photos[0].path");
		mockMvc.perform(get(path))
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(JPG));

		mockMvc.perform(multipart("/api/public/delivery-proofs/" + token + "/photos")
						.file(new MockMultipartFile("file", "note.txt", "image/jpeg", "not an image".getBytes())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DELIVERY_400_FILE"));
	}

	@Test
	void 서명을_못_받으면_차주가_종이_인수증_사진으로_대신한다() throws Exception {
		Trip trip = tripAtDropoff();
		issue(trip, null);

		mockMvc.perform(multipart("/api/trips/" + trip.getId() + "/delivery-proof/paper-photos")
						.file(new MockMultipartFile("file", "paper.jpg", "image/jpeg", JPG))
						.header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PHOTO"))
				.andExpect(jsonPath("$.photos[0].type").value("PAPER"));
		mockMvc.perform(get("/api/trips/" + trip.getId()).header("Authorization", bearer))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void 운행이_끝나고_7일이_지나면_링크가_만료된다() throws Exception {
		Trip trip = tripAtDropoff();
		String token = tokenOf(issue(trip, null));
		trip.complete(Instant.now().minus(Duration.ofDays(8)));
		tripRepository.flush();

		mockMvc.perform(get("/api/public/delivery-proofs/" + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DELIVERY_404"));
		mockMvc.perform(get("/api/public/delivery-proofs/없는토큰"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 취소된_운행과_남의_운행은_인수증을_받을_수_없다() throws Exception {
		Trip cancelled = tripAtDropoff();
		cancelled.cancel(Instant.now());
		issue(cancelled, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DELIVERY_409_CANCELLED"));

		User other = userRepository.save(User.create("01090001802", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		mockMvc.perform(post("/api/trips/" + tripAtDropoff().getId() + "/delivery-proof")
						.header("Authorization", "Bearer " + jwtProvider.issue(other.getId()).accessToken()))
				.andExpect(status().isNotFound());
	}

	private Trip tripAtDropoff() {
		Trip trip = Trip.create(driver, vehicle, null, "동네물류", 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pyeongtaek, null, daegu, null);
		trip.start(Instant.now().minusSeconds(6 * 3600));
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		return tripRepository.save(trip);
	}

	private ResultActions issue(Trip trip, String json) throws Exception {
		var request = post("/api/trips/" + trip.getId() + "/delivery-proof").header("Authorization", bearer);
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(request);
	}

	private ResultActions sign(String token, String json) throws Exception {
		return mockMvc.perform(post("/api/public/delivery-proofs/" + token + "/sign")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String tokenOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
	}
}
