package com.ieo.server.domain.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import com.ieo.server.domain.plan.repository.PlanPaymentRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DriverPlanControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private PlanPaymentRepository paymentRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private MockMvc mockMvc;
	private String bearer;
	private Long userId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		User user = userRepository.save(User.create("01090001801", UserRole.DRIVER));
		driverRepository.save(Driver.of(user));
		userId = user.getId();
		bearer = "Bearer " + jwtProvider.issue(userId).accessToken();
	}

	@Test
	void 처음에는_베이직이고_세_플랜과_한도를_알려준다() throws Exception {
		mockMvc.perform(get("/api/plans/me").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("BASIC"))
				.andExpect(jsonPath("$.trialAvailable").value(true))
				.andExpect(jsonPath("$.paidUntil").doesNotExist())
				.andExpect(jsonPath("$.usage.waitProofLimit").value(3))
				.andExpect(jsonPath("$.usage.taxInvoiceLimit").value(10))
				.andExpect(jsonPath("$.plans.length()").value(2))
				.andExpect(jsonPath("$.plans[1].price").value(14400))
				.andExpect(jsonPath("$.demoPayment").value(true));
	}

	@Test
	void 체험은_한_번이고_결제_기록을_남기지_않는다() throws Exception {
		mockMvc.perform(post("/api/plans/me/trial").header("Authorization", bearer))
				.andExpect(jsonPath("$.plan").value("PRO"))
				.andExpect(jsonPath("$.trial").value(true))
				.andExpect(jsonPath("$.renews").value(false))
				.andExpect(jsonPath("$.daysLeft").value(30))
				.andExpect(jsonPath("$.usage.waitProofLimit").doesNotExist())
				.andExpect(jsonPath("$.usage.taxInvoiceLimit").value(40));
		mockMvc.perform(post("/api/plans/me/trial").header("Authorization", bearer))
				.andExpect(status().isConflict());
		assertThat(paymentRepository.findAllByOwnerTypeAndOwnerIdOrderByPaidAtDesc(PlanOwnerType.DRIVER, userId)).isEmpty();
	}

	@Test
	void 유료_플랜을_고르면_데모_결제하고_해지하면_남은_날까지_쓴다() throws Exception {
		choose("PRO")
				.andExpect(jsonPath("$.plan").value("PRO"))
				.andExpect(jsonPath("$.renews").value(true));
		assertThat(paymentRepository.findAllByOwnerTypeAndOwnerIdOrderByPaidAtDesc(PlanOwnerType.DRIVER, userId))
				.singleElement()
				.satisfies(payment -> assertThat(payment.getAmount()).isEqualTo(14_400));

		choose("BASIC")
				.andExpect(jsonPath("$.plan").value("PRO"))
				.andExpect(jsonPath("$.renews").value(false));
		mockMvc.perform(post("/api/plans/me/resume").header("Authorization", bearer))
				.andExpect(jsonPath("$.renews").value(true));
		// 같은 플랜을 다시 골라도 두 번 결제하지 않는다
		choose("PRO");
		assertThat(paymentRepository.findAllByOwnerTypeAndOwnerIdOrderByPaidAtDesc(PlanOwnerType.DRIVER, userId)).hasSize(1);
	}

	private org.springframework.test.web.servlet.ResultActions choose(String plan) throws Exception {
		return mockMvc.perform(put("/api/plans/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"" + plan + "\"}"));
	}
}
