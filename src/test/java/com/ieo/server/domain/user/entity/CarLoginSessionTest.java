package com.ieo.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.user.enums.CarLoginStatus;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CarLoginSessionTest {

	private final Instant now = Instant.parse("2026-11-01T01:00:00Z");
	private final User driver = User.create("01012345678", UserRole.DRIVER);

	@Test
	void 만들면_5분_동안_승인을_기다린다() {
		CarLoginSession session = newSession();

		assertThat(session.statusAt(now)).isEqualTo(CarLoginStatus.PENDING);
		assertThat(session.getExpiresAt()).isEqualTo(now.plus(CarLoginSession.TTL));
		assertThat(session.statusAt(now.plus(CarLoginSession.TTL))).isEqualTo(CarLoginStatus.EXPIRED);
	}

	@Test
	void 승인하면_차량이_한_번만_받아_간다() {
		CarLoginSession session = newSession();

		session.approve(driver, now.plusSeconds(30));
		assertThat(session.statusAt(now.plusSeconds(31))).isEqualTo(CarLoginStatus.APPROVED);

		assertThat(session.consume(now.plusSeconds(32))).isSameAs(driver);
		assertThat(session.getStatus()).isEqualTo(CarLoginStatus.CONSUMED);
		assertError(() -> session.consume(now.plusSeconds(33)), ErrorCode.CAR_LOGIN_NOT_APPROVED);
	}

	@Test
	void 승인_전에는_받아_갈_수_없다() {
		CarLoginSession session = newSession();

		assertError(() -> session.consume(now), ErrorCode.CAR_LOGIN_NOT_APPROVED);
	}

	@Test
	void 만료_직전에_승인돼도_1분_안에는_받아_간다() {
		CarLoginSession session = newSession();
		Instant approvedAt = now.plus(CarLoginSession.TTL).minusSeconds(1);
		session.approve(driver, approvedAt);

		assertThat(session.consume(approvedAt.plusSeconds(30))).isSameAs(driver);
	}

	@Test
	void 승인되고_1분_안에_안_받아_가면_만료된다() {
		CarLoginSession session = newSession();
		session.approve(driver, now);

		Instant late = now.plus(CarLoginSession.CONSUME_WINDOW);
		assertThat(session.statusAt(late)).isEqualTo(CarLoginStatus.EXPIRED);
		assertError(() -> session.consume(late), ErrorCode.CAR_LOGIN_NOT_APPROVED);
	}

	@Test
	void 승인_거절은_한_번만_된다() {
		CarLoginSession session = newSession();
		session.reject(now);

		assertThat(session.statusAt(now)).isEqualTo(CarLoginStatus.REJECTED);
		assertError(() -> session.approve(driver, now), ErrorCode.CAR_LOGIN_ALREADY_DECIDED);
		assertError(() -> session.consume(now), ErrorCode.CAR_LOGIN_NOT_APPROVED);
	}

	@Test
	void 만료된_요청은_승인할_수_없다() {
		CarLoginSession session = newSession();

		assertError(() -> session.approve(driver, now.plus(CarLoginSession.TTL)), ErrorCode.CAR_LOGIN_EXPIRED);
	}

	@Test
	void 정지된_계정으로는_승인할_수_없다() {
		CarLoginSession session = newSession();
		driver.suspend("허위 오더 등록", now);

		assertError(() -> session.approve(driver, now), ErrorCode.USER_NOT_ACTIVE);
		assertThat(session.statusAt(now)).isEqualTo(CarLoginStatus.PENDING);
	}

	@Test
	void pollSecret_해시를_비교한다() {
		CarLoginSession session = newSession();

		assertThat(session.matchesPollSecret("secret-hash")).isTrue();
		assertThat(session.matchesPollSecret("other-hash")).isFalse();
	}

	private CarLoginSession newSession() {
		return CarLoginSession.create("KX7P-M4QZ", "secret-hash", "device-a", "Pleos 차량 앱", "0.3.0",
				"203.0.113.10", "평택시 근처", now);
	}

	private static void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}
