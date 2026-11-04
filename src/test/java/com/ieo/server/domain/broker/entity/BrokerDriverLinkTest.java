package com.ieo.server.domain.broker.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BrokerDriverLinkTest {

	private final Broker broker = Broker.create("○○물류", "1234567891", "제2026-001호", null);
	private final BrokerMember member = BrokerMember.of(User.create("01099998888", UserRole.BROKER), broker,
			BrokerMemberPosition.STAFF);
	private final Driver driver = Driver.of(User.create("01012345678", UserRole.DRIVER));
	private final Instant now = Instant.parse("2026-11-04T01:00:00Z");

	@Test
	void 요청하면_대기_중이고_수락하면_연결되며_수락_시각이_남는다() {
		BrokerDriverLink link = BrokerDriverLink.request(member, "01012345678");
		assertThat(link.statusAt(now)).isEqualTo(DriverLinkStatus.PENDING);

		link.accept(driver, now);

		assertThat(link.isLinked()).isTrue();
		assertThat(link.getDriver()).isSameAs(driver);
		assertThat(link.getLinkedAt()).isEqualTo(now);
	}

	@Test
	void 연결된_뒤에만_해제할_수_있고_해제하면_다시_해제할_수_없다() {
		BrokerDriverLink link = BrokerDriverLink.request(member, "01012345678");
		assertThatThrownBy(() -> link.unlink(now))
				.extracting("errorCode").isEqualTo(ErrorCode.DRIVER_LINK_CLOSED);

		link.accept(driver, now);
		link.unlink(now.plusSeconds(60));

		assertThat(link.getStatus()).isEqualTo(DriverLinkStatus.UNLINKED);
		assertThat(link.getUnlinkedAt()).isEqualTo(now.plusSeconds(60));
		assertThatThrownBy(() -> link.unlink(now))
				.extracting("errorCode").isEqualTo(ErrorCode.DRIVER_LINK_CLOSED);
	}

	@Test
	void 처리된_요청은_다시_수락_거절_취소할_수_없다() {
		BrokerDriverLink link = BrokerDriverLink.request(member, "01012345678");
		link.decline(now);

		assertThat(link.getStatus()).isEqualTo(DriverLinkStatus.DECLINED);
		assertThatThrownBy(() -> link.accept(driver, now))
				.extracting("errorCode").isEqualTo(ErrorCode.DRIVER_LINK_CLOSED);
		assertThatThrownBy(() -> link.cancel(now))
				.extracting("errorCode").isEqualTo(ErrorCode.DRIVER_LINK_CLOSED);
	}
}
