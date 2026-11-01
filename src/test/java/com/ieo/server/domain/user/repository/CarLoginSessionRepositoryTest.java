package com.ieo.server.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieo.server.domain.user.entity.CarLoginSession;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.CarLoginStatus;
import com.ieo.server.domain.user.enums.UserRole;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CarLoginSessionRepositoryTest {

	@Autowired
	private CarLoginSessionRepository carLoginSessionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	private final Instant now = Instant.parse("2026-11-01T01:00:00Z");

	@Test
	void 코드로_찾고_승인한_사용자를_저장한다() {
		User user = userRepository.save(User.create("01090000101", UserRole.DRIVER));
		CarLoginSession saved = carLoginSessionRepository.save(CarLoginSession.create(
				"KX7P-M4QZ", "secret-hash", "device-a", "Pleos 차량 앱", "0.3.0", "203.0.113.10", null, now));
		assertThat(saved.getId()).isNotNull();

		carLoginSessionRepository.findByCodeForUpdate("KX7P-M4QZ").orElseThrow().approve(user, now);
		entityManager.flush();
		entityManager.clear();

		CarLoginSession found = carLoginSessionRepository.findByIdForUpdate(saved.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(CarLoginStatus.APPROVED);
		assertThat(found.getApprovedUser().getId()).isEqualTo(user.getId());
		assertThat(carLoginSessionRepository.existsByCode("KX7P-M4QZ")).isTrue();
	}

	@Test
	void 오래된_세션을_지운다() {
		carLoginSessionRepository.save(CarLoginSession.create(
				"AAAA-BBBB", "hash", "device-a", null, null, null, null, now));

		assertThat(carLoginSessionRepository.deleteExpiredBefore(now)).isZero();
		assertThat(carLoginSessionRepository.deleteExpiredBefore(now.plus(CarLoginSession.TTL).plusSeconds(1))).isEqualTo(1);
	}
}
