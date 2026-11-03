package com.ieo.server.domain.broker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerServiceTest {

	private static final CreateBrokerRequest REQUEST =
			new CreateBrokerRequest("한빛로지스", "123-45-67891", "제2026-001호", "김대표");

	@Autowired
	private BrokerService brokerService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository memberRepository;

	@Test
	void 회사를_등록하면_등록한_계정이_대표가_된다() {
		User user = brokerUser("01090002001");

		MyBrokerResponse response = brokerService.register(user.getId(), REQUEST);

		assertThat(response.name()).isEqualTo("한빛로지스");
		assertThat(response.businessNumber()).isEqualTo("1234567891");
		assertThat(response.position()).isEqualTo(BrokerMemberPosition.OWNER);
		assertThat(response.positionLabel()).isEqualTo("대표");
		assertThat(response.settlementDays()).isEqualTo(30);
		BrokerMember member = memberRepository.findById(user.getId()).orElseThrow();
		assertThat(member.isOwner()).isTrue();
		assertThat(member.getBroker().getId()).isEqualTo(response.id());
	}

	@Test
	void 이미_등록된_사업자등록번호면_새_회사를_만들지_않는다() {
		brokerService.register(brokerUser("01090002001").getId(), REQUEST);
		User other = brokerUser("01090002002");

		assertError(() -> brokerService.register(other.getId(),
						new CreateBrokerRequest("다른이름", "1234567891", "제2026-009호", null)),
				ErrorCode.BROKER_DUPLICATE_BUSINESS_NUMBER);
		assertThat(brokerRepository.count()).isEqualTo(1);
		assertThat(memberRepository.existsById(other.getId())).isFalse();
	}

	@Test
	void 이미_소속된_담당자는_회사를_또_등록할_수_없다() {
		User user = brokerUser("01090002001");
		brokerService.register(user.getId(), REQUEST);

		assertError(() -> brokerService.register(user.getId(),
						new CreateBrokerRequest("두번째회사", "9876543215", "제2026-002호", null)),
				ErrorCode.BROKER_ALREADY_MEMBER);
	}

	@Test
	void 차주_계정은_회사를_등록할_수_없다() {
		User driver = userRepository.save(User.create("01090002003", UserRole.DRIVER));

		assertError(() -> brokerService.register(driver.getId(), REQUEST), ErrorCode.BROKER_NOT_BROKER);
		assertThat(brokerRepository.count()).isZero();
	}

	@Test
	void 탈퇴한_계정은_찾을_수_없다() {
		User user = brokerUser("01090002001");
		user.withdraw(null, Instant.now());

		assertError(() -> brokerService.register(user.getId(), REQUEST), ErrorCode.USER_NOT_FOUND);
	}

	@Test
	void 소속이_없으면_내_회사_조회는_404() {
		User user = brokerUser("01090002001");

		assertError(() -> brokerService.getMine(user.getId()), ErrorCode.BROKER_NO_MEMBERSHIP);
	}

	private User brokerUser(String phone) {
		return userRepository.save(User.create(phone, UserRole.BROKER));
	}

	private static void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run).extracting("errorCode").isEqualTo(errorCode);
	}
}
