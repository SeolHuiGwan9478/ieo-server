package com.ieo.server.domain.driver.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import org.junit.jupiter.api.Test;

class DriverTest {

	@Test
	void 차주_계정으로_차주_프로필을_만든다() {
		User user = User.create("01012345678", UserRole.DRIVER);

		Driver driver = Driver.of(user);

		assertThat(driver.getUser()).isSameAs(user);
	}

	@Test
	void 주선사_계정으로는_차주_프로필을_만들_수_없다() {
		User user = User.create("01012345678", UserRole.BROKER);

		assertThatThrownBy(() -> Driver.of(user)).isInstanceOf(IllegalArgumentException.class);
	}
}
