package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 주선사 플랜 (12장 PLAN-001~004, BILL-007): BASIC 기본, 대표만 전환, 리포트·담당자 수 검사 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerPlanControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BrokerService brokerService;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private User owner;
	private int phoneSeq;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		owner = user("01090010101", UserRole.BROKER, "김대표");
		brokerService.register(owner.getId(), new CreateBrokerRequest("한빛로지스", "1234567891", "제2026-001호", null));
	}

	@Test
	void 새_회사는_BASIC이고_대표가_플랜을_바꾸면_기능_목록이_바뀐다() throws Exception {
		perform(get("/api/brokers/me"), owner)
				.andExpect(jsonPath("$.plan.plan").value("BASIC"))
				.andExpect(jsonPath("$.plan.planLabel").value("베이직"))
				.andExpect(jsonPath("$.plan.features").isEmpty())
				.andExpect(jsonPath("$.plan.limits.members").value(1))
				.andExpect(jsonPath("$.plan.limits.shareLinksPerMonth").value(20))
				.andExpect(jsonPath("$.plan.startedAt").doesNotExist());

		changePlan(owner, "PRO")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.plan").value("PRO"))
				.andExpect(jsonPath("$.plan.features[*].code").value(Matchers.containsInAnyOrder("REPORTS", "DRIVER_RECORDS")))
				.andExpect(jsonPath("$.plan.features[0].label").exists())
				.andExpect(jsonPath("$.plan.limits.members").value(10))
				.andExpect(jsonPath("$.plan.limits.shareLinksPerMonth").doesNotExist())
				.andExpect(jsonPath("$.plan.startedAt").exists());
		changePlan(owner, "PRO_PLUS")
				.andExpect(jsonPath("$.plan.features.length()").value(4))
				.andExpect(jsonPath("$.plan.limits.members").doesNotExist());

		changePlan(owner, "GOLD").andExpect(status().isBadRequest());
		perform(put("/api/brokers/me/plan").contentType(MediaType.APPLICATION_JSON).content("{}"), owner)
				.andExpect(status().isBadRequest());
	}

	@Test
	void 리포트는_PRO부터이고_BASIC은_업그레이드_안내를_받는다() throws Exception {
		perform(get("/api/brokers/me/reports/routes"), owner)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_403_UPGRADE"))
				.andExpect(jsonPath("$.message").value(Matchers.containsString("프로 플랜부터")));
		perform(get("/api/brokers/me/reports/drivers"), owner)
				.andExpect(jsonPath("$.code").value("PLAN_403_UPGRADE"));

		changePlan(owner, "PRO");
		perform(get("/api/brokers/me/reports/routes"), owner).andExpect(status().isOk());
		perform(get("/api/brokers/me/reports/drivers"), owner).andExpect(status().isOk());

		// 내리면 다시 막힌다. 데이터는 그대로 (PLAN-004)
		changePlan(owner, "BASIC");
		perform(get("/api/brokers/me/reports/company"), owner).andExpect(status().isForbidden());
	}

	@Test
	void 담당자는_플랜을_못_바꾸고_담당자_수는_플랜_한도까지다() throws Exception {
		// BASIC은 대표 1명이 한도라 초대부터 막힌다
		invite(owner, "01090010201")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_403_MEMBER_LIMIT"));

		changePlan(owner, "PRO");
		User staff = user("01090010201", UserRole.BROKER, "이담당");
		long invitationId = idOf(invite(owner, "01090010201").andExpect(status().isCreated()));
		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.plan").value("PRO"));
		changePlan(staff, "PRO_PLUS")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_OWNER"));

		// 담당자 2명인 채로 BASIC으로 내려도 내보내지 않는다. 새 초대만 막힌다
		changePlan(owner, "BASIC").andExpect(status().isOk());
		perform(get("/api/brokers/me/members"), owner)
				.andExpect(jsonPath("$.length()").value(2));
		invite(owner, "01090010202")
				.andExpect(jsonPath("$.code").value("PLAN_403_MEMBER_LIMIT"));

		// 대기 중인 초대도 자리를 차지한다: PRO(10명)에서 담당자 2 + 초대 8이면 9번째 초대는 막힘
		changePlan(owner, "PRO");
		for (int i = 3; i <= 10; i++) {
			invite(owner, "010900102%02d".formatted(i)).andExpect(status().isCreated());
		}
		invite(owner, "01090010211")
				.andExpect(jsonPath("$.code").value("PLAN_403_MEMBER_LIMIT"));
	}

	@Test
	void 초대를_받은_뒤_플랜이_내려가면_수락할_때_막힌다() throws Exception {
		changePlan(owner, "PRO");
		User staff = user("01090010201", UserRole.BROKER, "이담당");
		long invitationId = idOf(invite(owner, "01090010201"));
		changePlan(owner, "BASIC");

		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_403_MEMBER_LIMIT"));
	}

	private ResultActions changePlan(User user, String plan) throws Exception {
		return perform(put("/api/brokers/me/plan").contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"" + plan + "\"}"), user);
	}

	private ResultActions invite(User user, String phone) throws Exception {
		return perform(post("/api/brokers/me/invitations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"), user);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
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
}
