package com.ieo.server.domain.broker.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import org.junit.jupiter.api.Test;

class BrokerMemberTest {

	private final Broker broker = Broker.create("○○물류", "1234567890", "제2026-001호", "홍길동");

	@Test
	void 주선사_계정으로_담당자_프로필을_만든다() {
		User user = User.create("01099998888", UserRole.BROKER);

		BrokerMember member = BrokerMember.of(user, broker, BrokerMemberPosition.OWNER);

		assertThat(member.getBroker()).isSameAs(broker);
		assertThat(member.isOwner()).isTrue();
	}

	@Test
	void 차주_계정으로는_담당자_프로필을_만들_수_없다() {
		User user = User.create("01012345678", UserRole.DRIVER);

		assertThatThrownBy(() -> BrokerMember.of(user, broker, BrokerMemberPosition.STAFF))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 정산_주기는_기본_30일() {
		assertThat(broker.getSettlementDays()).isEqualTo(30);

		broker.changeSettlementDays(45);
		assertThat(broker.getSettlementDays()).isEqualTo(45);

		assertThatThrownBy(() -> broker.changeSettlementDays(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}
