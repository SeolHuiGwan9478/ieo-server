package com.ieo.server.domain.broker.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BrokerInvitationTest {

	private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");

	private BrokerInvitation invitation;

	@BeforeEach
	void setUp() {
		Broker broker = Broker.create("한빛로지스", "1234567891", "제2026-001호", null);
		BrokerMember owner = BrokerMember.of(User.create("01090004001", UserRole.BROKER), broker, BrokerMemberPosition.OWNER);
		invitation = BrokerInvitation.create(owner, "01090004002");
		ReflectionTestUtils.setField(invitation, "createdAt", NOW);
	}

	@Test
	void 초대는_7일_뒤_만료로_본다() {
		assertThat(invitation.expiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
		assertThat(invitation.statusAt(NOW.plusSeconds(7 * 24 * 3600 - 1))).isEqualTo(BrokerInvitationStatus.PENDING);
		assertThat(invitation.statusAt(invitation.expiresAt())).isEqualTo(BrokerInvitationStatus.EXPIRED);
	}

	@Test
	void 수락하면_다시_처리할_수_없다() {
		invitation.accept(NOW);

		assertThat(invitation.getStatus()).isEqualTo(BrokerInvitationStatus.ACCEPTED);
		assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> invitation.cancel(NOW))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVITATION_CLOSED);
	}

	@Test
	void 만료된_초대는_수락할_수_없다() {
		assertThatThrownBy(() -> invitation.accept(invitation.expiresAt()))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVITATION_CLOSED);
	}

	@Test
	void 나간_담당자는_직책을_바꾸거나_다시_나갈_수_없다() {
		BrokerMember member = invitation.getInvitedBy();
		member.remove(NOW);

		assertThat(member.isActive()).isFalse();
		assertThatThrownBy(() -> member.changePosition(BrokerMemberPosition.STAFF))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_MEMBER_REMOVED);
		assertThatThrownBy(() -> member.remove(NOW))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_MEMBER_REMOVED);
	}
}
