package com.ieo.server.domain.broker.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieo.server.domain.broker.service.BrokerPlanService;
import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.domain.plan.repository.PlanPaymentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Autowired
	private BrokerPlanService planService;

	@Autowired
	private PlanPaymentRepository paymentRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

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
				.andExpect(jsonPath("$.plan.limits").doesNotExist())
				.andExpect(jsonPath("$.plan.startedAt").doesNotExist());

		changePlan(owner, "PRO")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.plan").value("PRO"))
				.andExpect(jsonPath("$.plan.price").value(39000))
				.andExpect(jsonPath("$.plan.features[*].code").value(Matchers.containsInAnyOrder("REPORTS", "DRIVER_RECORDS")))
				.andExpect(jsonPath("$.plan.features[0].label").exists())
				.andExpect(jsonPath("$.plan.startedAt").exists());

		// 프로 플러스는 뺐다 (2026-11-14)
		changePlan(owner, "PRO_PLUS").andExpect(status().isBadRequest());
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

		// 해지해도 남은 날까지는 프로. 기간이 끝나면 다시 막힌다. 데이터는 그대로 (PLAN-004)
		changePlan(owner, "BASIC");
		perform(get("/api/brokers/me/reports/company"), owner).andExpect(status().isOk());
		endPeriodNow();
		perform(get("/api/brokers/me/reports/company"), owner).andExpect(status().isForbidden());
	}

	@Test
	void 프로는_30일이고_해지하면_남은_날까지_쓰고_다시_고르면_해지만_취소한다() throws Exception {
		changePlan(owner, "PRO")
				.andExpect(jsonPath("$.plan.plan").value("PRO"))
				.andExpect(jsonPath("$.plan.periodEnd").exists())
				.andExpect(jsonPath("$.plan.cancelAtPeriodEnd").value(false));
		assertThat(payments()).hasSize(1);

		changePlan(owner, "BASIC")
				.andExpect(jsonPath("$.plan.plan").value("PRO"))
				.andExpect(jsonPath("$.plan.cancelAtPeriodEnd").value(true));
		changePlan(owner, "PRO")
				.andExpect(jsonPath("$.plan.cancelAtPeriodEnd").value(false));
		assertThat(payments()).hasSize(1);

		// 기간이 끝나면 새벽 정리가 다음 30일을 결제(데모)한다. 정리 전에도 프로로 보인다
		endPeriodNow();
		perform(get("/api/brokers/me"), owner)
				.andExpect(jsonPath("$.plan.plan").value("PRO"))
				.andExpect(jsonPath("$.plan.periodEnd").exists());
		planService.settleExpired();
		em.flush();
		em.clear();
		assertThat(payments()).hasSize(2);

		// 해지한 채 기간이 끝나면 베이직으로 내려간다
		changePlan(owner, "BASIC");
		endPeriodNow();
		planService.settleExpired();
		em.flush();
		em.clear();
		perform(get("/api/brokers/me"), owner)
				.andExpect(jsonPath("$.plan.plan").value("BASIC"))
				.andExpect(jsonPath("$.plan.periodEnd").doesNotExist());
		assertThat(payments()).hasSize(2);
	}

	/** 지금 기간을 방금 끝난 것으로 옮긴다 */
	private void endPeriodNow() {
		em.flush();
		jdbc.update("update broker_subscriptions set period_end = ? where broker_id = ?",
				java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), brokerId());
		em.clear();
	}

	private java.util.List<PlanPayment> payments() {
		return paymentRepository.findAllByOwnerTypeAndOwnerIdOrderByPaidAtDesc(PlanOwnerType.BROKER, brokerId());
	}

	private Long brokerId() {
		return jdbc.queryForObject("select broker_id from broker_members where user_id = ?", Long.class, owner.getId());
	}

	@Test
	void 담당자는_플랜을_못_바꾸고_담당자_수는_플랜과_상관없이_무제한이다() throws Exception {
		// 베이직에서도 여러 명을 초대하고 받을 수 있다 (2026-11-14)
		User staff = user("01090010201", UserRole.BROKER, "이담당");
		long invitationId = idOf(invite(owner, "01090010201").andExpect(status().isCreated()));
		perform(post("/api/broker-invitations/{id}/accept", invitationId), staff)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.plan").value("BASIC"));
		for (int i = 2; i <= 12; i++) {
			invite(owner, "010900102%02d".formatted(i)).andExpect(status().isCreated());
		}

		changePlan(staff, "PRO")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("BROKER_403_OWNER"));
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
