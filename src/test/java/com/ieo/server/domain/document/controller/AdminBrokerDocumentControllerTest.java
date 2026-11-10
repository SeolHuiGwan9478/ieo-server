package com.ieo.server.domain.document.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
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

/** 주선사 회사 서류 제출과 운영자 심사 (07 인계서 13장). 국세청은 스텁(application-test.yml)이라 회사 사업자번호 123-45-67800은 계속사업자로 나온다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminBrokerDocumentControllerTest {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
	private static final String BUSINESS_NUMBER = "123-45-67800";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private Long brokerId;
	private String ownerBearer;
	private String staffBearer;
	private String outsiderBearer;
	private String driverBearer;
	private String adminBearer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User ownerUser = userRepository.save(User.create("01077000021", UserRole.BROKER));
		ownerUser.changeName("박주선");
		Broker broker = brokerRepository.save(Broker.create("한빛물류", BUSINESS_NUMBER, "제2026-001호", "박주선"));
		brokerId = broker.getId();
		memberRepository.save(BrokerMember.of(ownerUser, broker, BrokerMemberPosition.OWNER));
		User staffUser = userRepository.save(User.create("01077000022", UserRole.BROKER));
		staffUser.changeName("김담당");
		memberRepository.save(BrokerMember.of(staffUser, broker, BrokerMemberPosition.STAFF));
		ownerBearer = bearer(ownerUser.getId());
		staffBearer = bearer(staffUser.getId());
		outsiderBearer = bearer(userRepository.save(User.create("01077000023", UserRole.BROKER)).getId());
		driverBearer = bearer(userRepository.save(User.create("01077000024", UserRole.DRIVER)).getId());
		User admin = userRepository.save(User.create("01077000029", UserRole.ADMIN));
		admin.changeName("운영자");
		adminBearer = bearer(admin.getId());
	}

	@Test
	void 소속_담당자가_아니면_회사_서류를_낼_수_없고_운영자가_아니면_심사할_수_없다() throws Exception {
		upload(driverBearer, "BROKER_LICENSE", png("l.png"), null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DOCUMENT_403_BROKER"));
		upload(outsiderBearer, "BROKER_LICENSE", png("l.png"), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_404_MEMBER"));
		mockMvc.perform(get("/api/admin/broker-documents").header(HttpHeaders.AUTHORIZATION, ownerBearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_403_ROLE"));
		admin("/api/admin/broker-documents")
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.pendingBrokers").value(0));
	}

	@Test
	void 주선사가_사업자등록증과_허가증을_내면_운영자가_심사하고_둘_다_승인되면_인증된다() throws Exception {
		// 담당자(STAFF)도 낼 수 있다. 허가증에는 사업자 정보가 없다
		long license = submit(staffBearer, "BROKER_LICENSE", null);
		// 사업자등록증은 개업일이 필수. 사업자번호·대표자는 회사 등록값으로 국세청에 조회한다
		upload(ownerBearer, "BUSINESS_REGISTRATION", png("b.png"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_BUSINESS"));
		String body = upload(ownerBearer, "BUSINESS_REGISTRATION", png("b.png"), "2018-05-14")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.businessCheck.businessNumber").value(BUSINESS_NUMBER))
				.andExpect(jsonPath("$.businessCheck.ownerName").value("박주선"))
				.andExpect(jsonPath("$.businessCheck.result").value("MATCH"))
				.andExpect(jsonPath("$.businessCheck.status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();
		long business = ((Number) JsonPath.read(body, "$.id")).longValue();
		myDocuments(staffBearer)
				.andExpect(jsonPath("$.verified").value(false))
				.andExpect(jsonPath("$.requiredTotal").value(2))
				.andExpect(jsonPath("$.documents[0].type").value("BUSINESS_REGISTRATION"))
				.andExpect(jsonPath("$.documents[1].type").value("BROKER_LICENSE"))
				.andExpect(jsonPath("$.documents[1].latest.status").value("PENDING"));

		// 목록: 회사 하나가 한 줄. 상호·사업자번호·허가 번호로 찾는다
		admin("/api/admin/broker-documents?status=PENDING")
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].brokerId").value(brokerId))
				.andExpect(jsonPath("$.items[0].name").value("한빛물류"))
				.andExpect(jsonPath("$.items[0].businessNumber").value(BUSINESS_NUMBER))
				.andExpect(jsonPath("$.items[0].licenseNumber").value("제2026-001호"))
				.andExpect(jsonPath("$.items[0].pendingCount").value(2))
				.andExpect(jsonPath("$.items[0].requiredTotal").value(2))
				.andExpect(jsonPath("$.items[0].businessCheckResult").value("MATCH"))
				.andExpect(jsonPath("$.pendingBrokers").value(1));
		admin("/api/admin/broker-documents?status=ALL&q=한빛").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/broker-documents?status=ALL&q=67800").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/broker-documents?status=ALL&q=2026-001").andExpect(jsonPath("$.items.length()").value(1));
		admin("/api/admin/broker-documents?status=ALL&q=없는회사").andExpect(jsonPath("$.items.length()").value(0));

		// 상세: 회사 등록 정보와 대표 담당자, 주선사용 반려 사유만
		admin("/api/admin/brokers/" + brokerId + "/documents")
				.andExpect(jsonPath("$.broker.name").value("한빛물류"))
				.andExpect(jsonPath("$.broker.ownerName").value("박주선"))
				.andExpect(jsonPath("$.broker.ownerPhone").value("010-****-0021"))
				.andExpect(jsonPath("$.broker.memberCount").value(2))
				.andExpect(jsonPath("$.documents.length()").value(2))
				.andExpect(jsonPath("$.documents[1].checks.length()").value(3))
				.andExpect(jsonPath("$.rejectReasons[*].code", hasItems("LICENSE_MISMATCH", "COMPANY_MISMATCH", "OTHER")))
				.andExpect(jsonPath("$.rejectReasons[*].code", not(hasItem("VEHICLE_MISMATCH"))))
				.andExpect(jsonPath("$.rejectReasons[*].code", not(hasItem("NAME_MISMATCH"))));

		// 사진: 운영자가 열면 이력에 남고, 소속 담당자만 볼 수 있다
		admin("/api/admin/broker-documents/" + license + "/file")
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(PNG));
		admin("/api/admin/brokers/" + brokerId + "/documents").andExpect(jsonPath("$.events[*].type", hasItem("VIEWED")));
		mockMvc.perform(get("/api/brokers/me/documents/" + license + "/file").header(HttpHeaders.AUTHORIZATION, staffBearer))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PNG));
		mockMvc.perform(get("/api/brokers/me/documents/" + license + "/file").header(HttpHeaders.AUTHORIZATION, outsiderBearer))
				.andExpect(status().isNotFound());

		// 차주용 사유로는 반려할 수 없다. 허가 번호 불일치로 반려하면 문구가 회사에 보인다
		reject(license, "{\"reasonCode\": \"VEHICLE_MISMATCH\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DOCUMENT_400_REASON"));
		reject(license, "{\"reasonCode\": \"LICENSE_MISMATCH\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rejectMessage").value(containsString("허가 번호")));
		myDocuments(ownerBearer)
				.andExpect(jsonPath("$.documents[1].latest.status").value("REJECTED"))
				.andExpect(jsonPath("$.documents[1].latest.rejectMessage").value(containsString("허가 번호")));

		// 다시 내고 둘 다 승인되면 인증됨
		long licenseAgain = submit(ownerBearer, "BROKER_LICENSE", null);
		approve(business).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		approve(licenseAgain).andExpect(status().isOk());
		approve(licenseAgain).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_409_REVIEWED"));
		myDocuments(ownerBearer).andExpect(jsonPath("$.verified").value(true)).andExpect(jsonPath("$.approvedRequired").value(2));
		admin("/api/admin/broker-documents?status=APPROVED").andExpect(jsonPath("$.items[0].verified").value(true));
		admin("/api/admin/broker-documents?status=PENDING").andExpect(jsonPath("$.items.length()").value(0));
		admin("/api/admin/brokers/" + brokerId + "/documents")
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.documents[1].history.length()").value(2))
				.andExpect(jsonPath("$.events[*].type", hasItems("SUBMITTED", "BUSINESS_CHECKED", "VIEWED", "APPROVED", "REJECTED")))
				.andExpect(jsonPath("$.events[*].actorName", hasItems("운영자", "김담당", "박주선")));
	}

	private String bearer(Long userId) {
		return "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	private static MockMultipartFile png(String name) {
		return new MockMultipartFile("file", name, "image/png", PNG);
	}

	private ResultActions upload(String bearer, String type, MockMultipartFile file, String openedOn) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/brokers/me/documents").file(file).param("type", type);
		if (openedOn != null) {
			request.param("openedOn", openedOn);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private long submit(String bearer, String type, String openedOn) throws Exception {
		String body = upload(bearer, type, png(type + ".png"), openedOn)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions myDocuments(String bearer) throws Exception {
		return mockMvc.perform(get("/api/brokers/me/documents").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
	}

	private ResultActions admin(String path) throws Exception {
		return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, adminBearer)).andExpect(status().isOk());
	}

	private ResultActions approve(long documentId) throws Exception {
		return mockMvc.perform(post("/api/admin/broker-documents/" + documentId + "/approve").header(HttpHeaders.AUTHORIZATION, adminBearer));
	}

	private ResultActions reject(long documentId, String json) throws Exception {
		return mockMvc.perform(post("/api/admin/broker-documents/" + documentId + "/reject")
				.header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}
}
