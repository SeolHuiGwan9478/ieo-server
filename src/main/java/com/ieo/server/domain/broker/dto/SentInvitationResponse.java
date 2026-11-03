package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerInvitation;
import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import java.time.Instant;

/** 우리 회사가 보낸 초대 */
public record SentInvitationResponse(
		Long id,
		String phone,
		BrokerInvitationStatus status,
		String statusLabel,
		String invitedByName,
		Instant createdAt,
		Instant expiresAt) {

	public static SentInvitationResponse from(BrokerInvitation invitation, Instant now) {
		BrokerInvitationStatus status = invitation.statusAt(now);
		return new SentInvitationResponse(
				invitation.getId(),
				invitation.getPhone(),
				status,
				status.getLabel(),
				invitation.getInvitedBy().getUser().getName(),
				invitation.getCreatedAt(),
				invitation.expiresAt());
	}
}
