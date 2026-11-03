package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerInvitation;
import java.time.Instant;

/** 내 번호로 온 초대. 수락을 기다리는 것만 내려준다 */
public record ReceivedInvitationResponse(
		Long id,
		Long brokerId,
		String brokerName,
		Instant createdAt,
		Instant expiresAt) {

	public static ReceivedInvitationResponse from(BrokerInvitation invitation) {
		return new ReceivedInvitationResponse(
				invitation.getId(),
				invitation.getBroker().getId(),
				invitation.getBroker().getName(),
				invitation.getCreatedAt(),
				invitation.expiresAt());
	}
}
