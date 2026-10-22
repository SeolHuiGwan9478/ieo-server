package com.ieo.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.global.error.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

	private final Instant now = Instant.parse("2026-10-22T00:00:00Z");

	@Test
	void 가입하면_ACTIVE() {
		User user = User.create("01012345678", UserRole.DRIVER);

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.isActive()).isTrue();
	}

	@Test
	void 정지했다가_다시_활성화() {
		User user = User.create("01012345678", UserRole.DRIVER);

		user.suspend("허위 오더 등록", now);
		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		assertThat(user.getStatusReason()).isEqualTo("허위 오더 등록");
		assertThat(user.getStatusChangedAt()).isEqualTo(now);

		user.reactivate(now);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getStatusReason()).isNull();
	}

	@Test
	void 정지된_계정은_로그인할_수_없다() {
		User user = User.create("01012345678", UserRole.DRIVER);
		user.suspend("허위 오더 등록", now);

		assertThatThrownBy(() -> user.recordLogin(now)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 탈퇴하면_휴대폰과_이름을_지운다() {
		User user = User.create("01012345678", UserRole.DRIVER);
		user.changeName("설기사");

		user.withdraw("본인 요청", now);

		assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(user.getPhone()).isNull();
		assertThat(user.getName()).isNull();
	}

	@Test
	void 탈퇴한_계정은_되돌릴_수_없다() {
		User user = User.create("01012345678", UserRole.DRIVER);
		user.withdraw("본인 요청", now);

		assertThatThrownBy(() -> user.reactivate(now)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> user.withdraw("다시", now)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> user.changeName("설기사")).isInstanceOf(BusinessException.class);
	}
}
