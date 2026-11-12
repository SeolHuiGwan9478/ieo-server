package com.ieo.server.domain.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
	void 하차지에서_종이_인수증_사진을_올리고_보내면_지급_예정일이_보낸_날부터_잡힌다() throws Exception {
		Trip trip = tripAtDropoff(PaymentType.RECEIPT);

		proof(trip)
				.andExpect(jsonPath("$.required").value(true))
				.andExpect(jsonPath("$.status").value("NONE"))
				.andExpect(jsonPath("$.canUpload").value(true))
				.andExpect(jsonPath("$.photos.length()").value(0));
		upload(trip, JPG).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
		String body = upload(trip, PNG)
				.andExpect(jsonPath("$.photos.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		String path = JsonPath.read(body, "$.photos[1].path");
		mockMvc.perform(get(path).header("Authorization", bearer))
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(PNG));

		Integer first = JsonPath.read(body, "$.photos[0].id");
		mockMvc.perform(delete("/api/trips/" + trip.getId() + "/delivery-proof/photos/" + first).header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photos.length()").value(1));

		completeTrip(trip);
		send(trip).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"))
				.andExpect(jsonPath("$.sentAt").exists())
				.andExpect(jsonPath("$.canUpload").value(false));

		String today = KoreanDate.of(Instant.now()).toString();
		mockMvc.perform(get("/api/ledger").header("Authorization", bearer).param("from", today).param("to", today))
				.andExpect(jsonPath("$[0].expectedPaymentDate").value(KoreanDate.of(Instant.now()).plusDays(30).toString()))
				.andExpect(jsonPath("$[0].receiptSentAt").exists());
	}

	@Test
	void 보낸_뒤에는_사진을_바꾸거나_다시_보낼_수_없다() throws Exception {
		Trip trip = tripAtDropoff(PaymentType.RECEIPT);
		String body = upload(trip, JPG).andReturn().getResponse().getContentAsString();
		Integer photoId = JsonPath.read(body, "$.photos[0].id");
		send(trip).andExpect(status().isOk());

		upload(trip, JPG).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_409_SENT"));
		send(trip).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_409_SENT"));
		mockMvc.perform(delete("/api/trips/" + trip.getId() + "/delivery-proof/photos/" + photoId).header("Authorization", bearer))
				.andExpect(jsonPath("$.code").value("DELIVERY_409_SENT"));
	}

	@Test
	void 사진_없이는_보낼_수_없고_사진은_다섯_장까지다() throws Exception {
		Trip trip = tripAtDropoff(PaymentType.RECEIPT);
		send(trip).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_409_NO_PHOTO"));

		for (int i = 0; i < 5; i++) {
			upload(trip, JPG).andExpect(status().isOk());
		}
		proof(trip).andExpect(jsonPath("$.canUpload").value(false));
		upload(trip, JPG).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DELIVERY_400_PHOTO_LIMIT"));
		upload(trip, "not an image".getBytes()).andExpect(jsonPath("$.code").value("DELIVERY_400_PHOTO_LIMIT"));
	}

	@Test
	void 사진이_아니면_거부한다() throws Exception {
		upload(tripAtDropoff(PaymentType.RECEIPT), "not an image".getBytes())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DELIVERY_400_FILE"));
	}

	@Test
	void 인수증_결제가_아니거나_하차지_전이거나_취소된_운행은_받지_않는다() throws Exception {
		Trip prepaid = tripAtDropoff(PaymentType.PREPAID);
		proof(prepaid).andExpect(jsonPath("$.required").value(false)).andExpect(jsonPath("$.canUpload").value(false));
		upload(prepaid, JPG).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_409_NOT_RECEIPT"));

		Trip early = Trip.create(driver, vehicle, null, "동네물류", 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pyeongtaek, null, daegu, null);
		early.start(Instant.now());
		tripRepository.save(early);
		proof(early).andExpect(jsonPath("$.canUpload").value(false));
		upload(early, JPG).andExpect(jsonPath("$.code").value("DELIVERY_409_TOO_EARLY"));

		Trip cancelled = tripAtDropoff(PaymentType.RECEIPT);
		cancelled.cancel(Instant.now());
		upload(cancelled, JPG).andExpect(jsonPath("$.code").value("DELIVERY_409_CANCELLED"));
	}

	@Test
	void 남의_운행_인수증은_볼_수_없다() throws Exception {
		Trip trip = tripAtDropoff(PaymentType.RECEIPT);
		String body = upload(trip, JPG).andReturn().getResponse().getContentAsString();
		String path = JsonPath.read(body, "$.photos[0].path");

		User other = userRepository.save(User.create("01090001802", UserRole.DRIVER));
		driverRepository.save(Driver.of(other));
		String otherBearer = "Bearer " + jwtProvider.issue(other.getId()).accessToken();
		mockMvc.perform(get("/api/trips/" + trip.getId() + "/delivery-proof").header("Authorization", otherBearer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(path).header("Authorization", otherBearer)).andExpect(status().isNotFound());
	}

	private Trip tripAtDropoff(PaymentType paymentType) {
		Trip trip = Trip.create(driver, vehicle, null, "동네물류", 450_000, paymentType, TripSource.MANUAL,
				pyeongtaek, null, daegu, null);
		trip.start(Instant.now().minusSeconds(6 * 3600));
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		return tripRepository.save(trip);
	}

	private void completeTrip(Trip trip) throws Exception {
		mockMvc.perform(post("/api/trips/" + trip.getId() + "/complete").header("Authorization", bearer))
				.andExpect(status().isOk());
	}

	private ResultActions proof(Trip trip) throws Exception {
		return mockMvc.perform(get("/api/trips/" + trip.getId() + "/delivery-proof").header("Authorization", bearer));
	}

	private ResultActions upload(Trip trip, byte[] content) throws Exception {
		return mockMvc.perform(multipart("/api/trips/" + trip.getId() + "/delivery-proof/photos")
				.file(new MockMultipartFile("file", "paper.jpg", "image/jpeg", content))
				.header("Authorization", bearer));
	}

	private ResultActions send(Trip trip) throws Exception {
		return mockMvc.perform(post("/api/trips/" + trip.getId() + "/delivery-proof/send").header("Authorization", bearer));
	}
}
