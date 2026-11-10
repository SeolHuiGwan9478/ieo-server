package com.ieo.server.domain.document.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.document.repository.DriverDocumentEventRepository;
import com.ieo.server.domain.document.repository.DriverDocumentRepository;
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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminDocumentControllerTest {

	/** PNG 매직 바이트만 있는 가짜 사진. 서버는 파일 앞부분으로만 형식을 본다 */
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

	/** 스텁 국세청(application-test.yml의 ieo.nts.stub)은 사업자번호 아홉째 자리로 결과를 정한다: 0 계속사업자, 8 폐업, 9 불일치. 마지막 자리는 검증 숫자 */
	private static final String MATCH_NUMBER = "123-45-67800";
	private static final String CLOSED_NUMBER = "1234567886";
	private static final String MISMATCH_NUMBER = "1234567891";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DriverDocumentRepository documentRepository;

	@Autowired
	private DriverDocumentEventRepository eventRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private Long driverId;
	private String driverBearer;
	private String adminBearer;
	private String brokerBearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User driverUser = userRepository.save(User.create("01077000001", UserRole.DRIVER));
		driverUser.changeName("김기사");
		Driver driver = driverRepository.save(Driver.of(driverUser));
		vehicleRepository.save(Vehicle.create(driver, "경기80바1234", Tonnage.TON_5, BodyType.CARGO, new BigDecimal("3.6")));
		driverId = driverUser.getId();
		driverBearer = bearer(driverId);

		User admin = userRepository.save(User.create("01077000009", UserRole.ADMIN));
		admin.changeName("운영자");
		adminBearer = bearer(admin.getId());
		brokerBearer = bearer(userRepository.save(User.create("01077000002", UserRole.BROKER)).getId());
	}

	@Test
	void 운영자가_아니면_심사_API를_쓸_수_없다() throws Exception {
		mockMvc.perform(get("/api/admin/driver-documents"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/driver-documents").header(HttpHeaders.AUTHORIZATION, driverBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_403_ROLE"));
		mockMvc.perform(get("/api/admin/driver-documents").header(HttpHeaders.AUTHORIZATION, brokerBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_403_ROLE"));
		admin("/api/admin/driver-documents")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.pendingDrivers").value(0));
	}

	@Test
	void 차주가_낸_서류를_운영자가_승인하거나_사유를_적어_반려하고_필수_서류가_모두_승인되면_인증된다() throws Exception {
		// 올리기: 차주만, JPG·PNG만, 심사 중인 종류는 다시 못 올린다
		upload(brokerBearer, "BUSINESS_REGISTRATION", png("a.png"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DOCUMENT_403_ROLE"));
		upload(driverBearer, "BUSINESS_REGISTRATION", new MockMultipartFile("file", "a.txt", "image/png", "not an image".getBytes()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_FILE"));
		upload(driverBearer, "BUSINESS_REGISTRATION", png("b.png"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_BUSINESS"));
		long business = submitBusiness(MATCH_NUMBER, "2020-01-31");
		upload(driverBearer, "BUSINESS_REGISTRATION", png("again.png"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_409_PENDING"));
		driverDocuments()
				.andExpect(jsonPath("$.verified").value(false))
				.andExpect(jsonPath("$.requiredTotal").value(4))
				.andExpect(jsonPath("$.documents[0].type").value("BUSINESS_REGISTRATION"))
				.andExpect(jsonPath("$.documents[0].latest.status").value("PENDING"))
				.andExpect(jsonPath("$.documents[1].latest").isEmpty());

		// 심사 목록: 차주 한 명이 한 줄, 휴대폰은 가려서
		admin("/api/admin/driver-documents?status=PENDING")
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].driverId").value(driverId))
				.andExpect(jsonPath("$.items[0].name").value("김기사"))
				.andExpect(jsonPath("$.items[0].phone").value("010-****-0001"))
				.andExpect(jsonPath("$.items[0].plateNumber").value("경기80바1234"))
				.andExpect(jsonPath("$.items[0].pendingCount").value(1))
				.andExpect(jsonPath("$.items[0].approvedRequired").value(0))
				.andExpect(jsonPath("$.items[0].requiredTotal").value(4))
				.andExpect(jsonPath("$.items[0].businessCheckResult").value("MATCH"))
				.andExpect(jsonPath("$.items[0].businessAttention").value(false))
				.andExpect(jsonPath("$.pendingDrivers").value(1));
		admin("/api/admin/driver-documents?status=APPROVED").andExpect(jsonPath("$.items.length()").value(0));
		admin("/api/admin/driver-documents?status=ALL&q=기사").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/driver-documents?status=ALL&q=0001").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/driver-documents?status=ALL&q=80바1234").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/driver-documents?status=ALL&q=없는사람").andExpect(jsonPath("$.items.length()").value(0));

		// 상세와 열람: 대조할 앱 입력값이 같이 오고, 사진을 열면 이력에 남는다
		admin("/api/admin/drivers/" + driverId + "/documents")
				.andExpect(jsonPath("$.driver.name").value("김기사"))
				.andExpect(jsonPath("$.vehicle.plateNumber").value("경기80바1234"))
				.andExpect(jsonPath("$.vehicle.tonnageLabel").value("5톤"))
				.andExpect(jsonPath("$.documents[0].latest.id").value(business))
				.andExpect(jsonPath("$.documents[0].checks.length()").value(4))
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.result").value("MATCH"))
				.andExpect(jsonPath("$.rejectReasons[*].code", hasItem("OTHER")));
		admin("/api/admin/driver-documents/" + business + "/file")
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(PNG));
		admin("/api/admin/drivers/" + driverId + "/documents")
				.andExpect(jsonPath("$.events[*].type", hasItem("VIEWED")));
		mockMvc.perform(get("/api/drivers/me/documents/" + business + "/file").header(HttpHeaders.AUTHORIZATION, driverBearer))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PNG));

		// 승인은 한 번만
		approve(business).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		approve(business).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_409_REVIEWED"));

		// 반려는 사유가 필수, 기타는 직접 적어야 한다. 문구가 차주에게 그대로 보인다
		long license = submit("DRIVER_LICENSE");
		reject(license, "{\"reasonCode\": \"OTHER\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_REASON"));
		reject(license, "{\"reasonCode\": \"NAME_MISMATCH\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectMessage").value("가입한 이름과 서류의 이름이 달라요"));
		driverDocuments()
				.andExpect(jsonPath("$.documents[2].type").value("DRIVER_LICENSE"))
				.andExpect(jsonPath("$.documents[2].latest.status").value("REJECTED"))
				.andExpect(jsonPath("$.documents[2].latest.rejectMessage").value("가입한 이름과 서류의 이름이 달라요"));
		admin("/api/admin/driver-documents?status=REJECTED").andExpect(jsonPath("$.items[0].driverId").value(driverId));

		// 반려된 서류만 다시 내면 새 건이 생기고, 필수 서류가 모두 승인되면 인증된다
		long licenseAgain = submit("DRIVER_LICENSE");
		admin("/api/admin/driver-documents?status=PENDING").andExpect(jsonPath("$.items[0].resubmitted").value(true));
		approve(licenseAgain).andExpect(status().isOk());
		approve(submit("VEHICLE_REGISTRATION")).andExpect(status().isOk());
		driverDocuments().andExpect(jsonPath("$.verified").value(false)).andExpect(jsonPath("$.approvedRequired").value(3));
		approve(submit("FREIGHT_QUALIFICATION")).andExpect(status().isOk());
		driverDocuments().andExpect(jsonPath("$.verified").value(true));
		admin("/api/admin/driver-documents?status=APPROVED").andExpect(jsonPath("$.items[0].verified").value(true));
		admin("/api/admin/driver-documents?status=PENDING").andExpect(jsonPath("$.items.length()").value(0));
		admin("/api/admin/drivers/" + driverId + "/documents")
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.documents[2].history.length()").value(2))
				.andExpect(jsonPath("$.events[*].type", hasItems("SUBMITTED", "BUSINESS_CHECKED", "VIEWED", "APPROVED", "REJECTED")))
				.andExpect(jsonPath("$.events[*].actorName", hasItems("운영자", "김기사")));
	}

	@Test
	void 사업자등록증은_국세청_진위확인_결과가_같이_오고_운영자가_다시_조회할_수_있다() throws Exception {
		// 번호는 검증 숫자까지 맞아야 하고, 개업일 형식이 틀리면 400. 다른 서류에는 사업자 정보를 보낼 수 없다
		uploadBusiness("123-45-67890", "2020-01-31")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_BUSINESS"));
		uploadBusiness(MATCH_NUMBER, "2020-13-01")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_BUSINESS"));
		upload(driverBearer, "DRIVER_LICENSE", png("l.png"), MATCH_NUMBER, "2020-01-31")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));

		// 불일치: 제출 응답과 목록·상세에 눈에 띄게 온다. 대표자를 안 적으면 가입자 이름으로 조회한다
		String body = uploadBusiness(MISMATCH_NUMBER, "20200131")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.businessCheck.businessNumber").value("123-45-67891"))
				.andExpect(jsonPath("$.businessCheck.openedOn").value("2020-01-31"))
				.andExpect(jsonPath("$.businessCheck.ownerName").value("김기사"))
				.andExpect(jsonPath("$.businessCheck.result").value("MISMATCH"))
				.andExpect(jsonPath("$.businessCheck.attention").value(true))
				.andExpect(jsonPath("$.businessCheck.stub").value(true))
				.andReturn().getResponse().getContentAsString();
		long mismatch = ((Number) JsonPath.read(body, "$.id")).longValue();
		admin("/api/admin/driver-documents?status=PENDING")
				.andExpect(jsonPath("$.items[0].businessCheckResult").value("MISMATCH"))
				.andExpect(jsonPath("$.items[0].businessAttention").value(true));
		admin("/api/admin/drivers/" + driverId + "/documents")
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.summary").value(containsString("국세청 등록 정보와 달라요")))
				.andExpect(jsonPath("$.events[0].type").value("BUSINESS_CHECKED"))
				.andExpect(jsonPath("$.rejectReasons[*].code", hasItems("BUSINESS_MISMATCH", "BUSINESS_CLOSED")));

		// 운영자가 다시 조회할 수 있고, 반려 사유에 국세청 불일치가 있다. 처리된 서류는 다시 조회하지 못한다
		recheck(mismatch).andExpect(status().isOk()).andExpect(jsonPath("$.businessCheck.result").value("MISMATCH"));
		reject(mismatch, "{\"reasonCode\": \"BUSINESS_MISMATCH\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rejectMessage").value(containsString("국세청 등록 정보와 달라요")));
		recheck(mismatch).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_409_REVIEWED"));

		// 폐업자: 등록 정보는 일치하지만 눈여겨볼 건. 사업자등록증이 아닌 서류는 조회할 수 없다
		long closed = submitBusiness(CLOSED_NUMBER, "2019-05-02");
		admin("/api/admin/drivers/" + driverId + "/documents")
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.result").value("MATCH"))
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.status").value("CLOSED"))
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.closedOn").value("2022-05-02"))
				.andExpect(jsonPath("$.documents[0].latest.businessCheck.attention").value(true))
				.andExpect(jsonPath("$.documents[0].history.length()").value(2));
		recheck(submit("DRIVER_LICENSE")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
		reject(closed, "{\"reasonCode\": \"BUSINESS_CLOSED\"}").andExpect(status().isOk());
	}

	@Test
	void 탈퇴하면_서류와_이력이_모두_지워진다() throws Exception {
		submitBusiness(MATCH_NUMBER, "2020-01-31");
		mockMvc.perform(post("/api/users/me/withdraw").header(HttpHeaders.AUTHORIZATION, driverBearer))
				.andExpect(status().is2xxSuccessful());
		assertTrue(documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(driverId).isEmpty());
		assertTrue(eventRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(driverId).isEmpty());
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	private static MockMultipartFile png(String name) {
		return new MockMultipartFile("file", name, "image/png", PNG);
	}

	private ResultActions upload(String bearer, String type, MockMultipartFile file) throws Exception {
		return upload(bearer, type, file, null, null);
	}

	private ResultActions upload(String bearer, String type, MockMultipartFile file, String businessNumber, String openedOn) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/drivers/me/documents").file(file).param("type", type);
		if (businessNumber != null) {
			request.param("businessNumber", businessNumber).param("openedOn", openedOn);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private ResultActions uploadBusiness(String businessNumber, String openedOn) throws Exception {
		return upload(driverBearer, "BUSINESS_REGISTRATION", png("business.png"), businessNumber, openedOn);
	}

	private long submitBusiness(String businessNumber, String openedOn) throws Exception {
		String body = uploadBusiness(businessNumber, openedOn)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions recheck(long documentId) throws Exception {
		return mockMvc.perform(post("/api/admin/driver-documents/" + documentId + "/business-check").header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

	private long submit(String type) throws Exception {
		String body = upload(driverBearer, type, png(type + ".png"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions driverDocuments() throws Exception {
		return mockMvc.perform(get("/api/drivers/me/documents").header(HttpHeaders.AUTHORIZATION, driverBearer))
				.andExpect(status().isOk());
	}

	private ResultActions admin(String path) throws Exception {
		return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminBearer)).andExpect(status().isOk());
	}

	private ResultActions approve(long documentId) throws Exception {
		return mockMvc.perform(post("/api/admin/driver-documents/" + documentId + "/approve").header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

	private ResultActions reject(long documentId, String json) throws Exception {
		return mockMvc.perform(post("/api/admin/driver-documents/" + documentId + "/reject")
				.header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}
}
