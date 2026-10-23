package com.ieo.server.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.enums.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserConsentRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserConsentRepository userConsentRepository;

	@Test
	void 종류별로_가장_최근_행이_현재_상태() {
		User user = userRepository.save(User.create("01090000001", UserRole.DRIVER));
		User other = userRepository.save(User.create("01090000002", UserRole.DRIVER));
		userConsentRepository.save(UserConsent.of(user, ConsentType.TERMS, "2026-10-01", true));
		userConsentRepository.save(UserConsent.of(user, ConsentType.MARKETING, "2026-10-01", true));
		userConsentRepository.save(UserConsent.of(user, ConsentType.MARKETING, "2026-10-01", false));
		userConsentRepository.save(UserConsent.of(other, ConsentType.MARKETING, "2026-10-01", true));

		List<UserConsent> latest = userConsentRepository.findLatestByUserId(user.getId());

		assertThat(latest).extracting(UserConsent::getType, UserConsent::isAgreed)
			.containsExactlyInAnyOrder(
				tuple(ConsentType.TERMS, true),
				tuple(ConsentType.MARKETING, false));
		assertThat(userConsentRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), ConsentType.MARKETING))
			.get().extracting(UserConsent::isAgreed).isEqualTo(false);
		assertThat(userConsentRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId())).hasSize(3);
	}
}
