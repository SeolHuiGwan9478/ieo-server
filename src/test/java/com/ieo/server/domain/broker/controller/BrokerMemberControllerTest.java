package com.ieo.server.domain.broker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.BrokerInvitation;
import com.ieo.server.domain.broker.repository.BrokerInvitationRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
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
class BrokerMemberControllerTest {

	private static final String STAFF_PHONE = "01090005002";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BrokerService brokerService;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Autowired
	private BrokerInvitationRepository invitationRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private User owner;
	private User staff;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = brokerUser("01090005001", "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
		staff = brokerUser(STAFF_PHONE, "이담당");
	}

	@Test
	void 대표가_초대하고_번호_주인이_수락하면_담당자가_된다() throws Exception {
		long invitationId = invite(STAFF_PHONE);

		perform(get("/api/broker-invitations"), staff)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(invitationId))
				.andExpect(jsonPath("$[0].brokerName").value("한빛로지스"));

		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("한빛로지스"))
				.andExpect(jsonPath("$.position").value("STAFF"));

		perform(get("/api/brokers/me/members"), staff)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("김대표"))
				.andExpect(jsonPath("$[0].positionLabel").value("대표"))
				.andExpect(jsonPath("$[1].name").value("이담당"))
				.andExpect(jsonPath("$[1].me").value(true));
		perform(get("/api/brokers/me/invitations"), owner)
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 담당자는_초대할_수_없다() throws Exception {
		join(staff);

		perform(post("/api/brokers/me/invitations").contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"01090005009\"}"), staff)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_OWNER"));
	}

	@Test
	void 같은_번호를_두_번_초대하거나_이미_담당자인_번호는_막는다() throws Exception {
		invite(STAFF_PHONE);
		inviteRequest(STAFF_PHONE)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_INVITATION_409_DUPLICATE"));

		inviteRequest("01090005001")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_INVITATION_409_MEMBER"));
	}

	@Test
	void 다른_번호로_온_초대는_보이지_않는다() throws Exception {
		long invitationId = invite("01090005009");

		perform(get("/api/broker-invitations"), staff).andExpect(jsonPath("$.length()").value(0));
		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_INVITATION_404"));
	}

	@Test
	void 만료되거나_취소된_초대는_수락할_수_없다() throws Exception {
		long expiredId = invite(STAFF_PHONE);
		BrokerInvitation expired = invitationRepository.findById(expiredId).orElseThrow();
		ReflectionTestUtils.setField(expired, "createdAt", Instant.now().minus(BrokerInvitation.TTL));

		perform(post("/api/broker-invitations/{id}/accept", expiredId), staff)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_INVITATION_409_CLOSED"));

		long canceledId = invite(STAFF_PHONE);
		perform(post("/api/brokers/me/invitations/{id}/cancel", canceledId), owner)
				.andExpect(status().isNoContent());
		perform(post("/api/broker-invitations/{id}/accept", canceledId), staff)
				.andExpect(status().isConflict());
	}

	@Test
	void 차주로_가입된_번호는_초대를_수락할_수_없다() throws Exception {
		User driver = userRepository.save(User.create("01090005003", UserRole.DRIVER));
		long invitationId = invite("01090005003");

		perform(post("/api/broker-invitations/{id}/accept", invitationId), driver)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_ROLE"))
				.andExpect(jsonPath("$.message").value("차주로 가입된 번호는 주선사 담당자가 될 수 없습니다."));
	}

	@Test
	void 다른_회사_소속이면_수락할_수_없다() throws Exception {
		brokerService.register(staff.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));
		long invitationId = invite(STAFF_PHONE);

		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_409_MEMBER"));
	}

	@Test
	void 대표를_여러_명_둘_수_있지만_마지막_대표는_내릴_수_없다() throws Exception {
		join(staff);
		perform(patch("/api/brokers/me/members/{id}", owner.getId()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"position\":\"STAFF\"}"), owner)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_409_LAST_OWNER"));

		perform(patch("/api/brokers/me/members/{id}", staff.getId()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"position\":\"OWNER\"}"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.position").value("OWNER"));
		perform(patch("/api/brokers/me/members/{id}", owner.getId()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"position\":\"STAFF\"}"), owner)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positionLabel").value("담당자"));
	}

	@Test
	void 내보낸_담당자는_회사_기능을_쓸_수_없고_다른_회사에도_못_들어간다() throws Exception {
		join(staff);

		perform(post("/api/brokers/me/members/{id}/remove", staff.getId()), owner)
				.andExpect(status().isNoContent());

		assertThat(memberRepository.findById(staff.getId()).orElseThrow().getRemovedAt()).isNotNull();
		perform(get("/api/brokers/me"), staff)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_REMOVED"));
		perform(get("/api/broker/orders"), staff)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_REMOVED"));
		perform(post("/api/brokers").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"새회사\",\"businessNumber\":\"9876543215\",\"licenseNumber\":\"제2026-002호\"}"), staff)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_REMOVED"));
		perform(get("/api/brokers/me/members"), owner)
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 다른_회사_담당자는_내보낼_수_없다() throws Exception {
		User other = brokerUser("01090005007", "남");
		brokerService.register(other.getId(), new CreateBrokerRequest("경쟁물류", "9876543215", "제2026-002호", null));

		perform(post("/api/brokers/me/members/{id}/remove", other.getId()), owner)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BROKER_MEMBER_404"));
	}

	@Test
	void 담당자는_스스로_나갈_수_있지만_마지막_대표는_나가거나_탈퇴할_수_없다() throws Exception {
		join(staff);
		perform(post("/api/brokers/me/leave"), staff).andExpect(status().isNoContent());

		perform(post("/api/brokers/me/leave"), owner)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_409_LAST_OWNER"));
		perform(post("/api/users/me/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"), owner)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROKER_409_LAST_OWNER"));
		assertThat(userRepository.findById(owner.getId()).orElseThrow().getPhone()).isNotNull();
	}

	@Test
	void 담당자가_탈퇴하면_회사에서_빠지고_받은_초대도_지워진다() throws Exception {
		join(staff);
		User invitee = brokerUser("01090005008", "박초대");
		invite("01090005008");

		perform(post("/api/users/me/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"), staff)
				.andExpect(status().isNoContent());
		perform(post("/api/users/me/withdraw").contentType(MediaType.APPLICATION_JSON).content("{}"), invitee)
				.andExpect(status().isNoContent());

		perform(get("/api/brokers/me/members"), owner)
				.andExpect(jsonPath("$.length()").value(1));
		perform(get("/api/brokers/me/invitations"), owner)
				.andExpect(jsonPath("$.length()").value(0));
	}

	private void join(User user) throws Exception {
		long invitationId = invite(user.getPhone());
		perform(post("/api/broker-invitations/{id}/accept", invitationId), user).andExpect(status().isOk());
	}

	private long invite(String phone) throws Exception {
		String body = inviteRequest(phone)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions inviteRequest(String phone) throws Exception {
		return perform(post("/api/brokers/me/invitations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"), owner);
	}

	private ResultActions perform(
			MockHttpServletRequestBuilder request, User user) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + jwtProvider.issue(user.getId()).accessToken()));
	}

	private User brokerUser(String phone, String name) {
		User user = User.create(phone, UserRole.BROKER);
		user.changeName(name);
		return userRepository.save(user);
	}
}
