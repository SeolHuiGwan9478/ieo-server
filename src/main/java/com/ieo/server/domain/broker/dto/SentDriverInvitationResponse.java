package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import java.time.Instant;

/** 우리 회사가 보낸 단골 연결 요청. 차주가 가입했는지는 알려 주지 않는다 */
public record SentDriverInvitationResponse(
		Long id,
		String phone,
		DriverLinkStatus status,
		String statusLabel,
		String invitedByName,
		Instant createdAt,
		Instant expiresAt) {

	public static SentDriverInvitationResponse from(BrokerDriverLink link, Instant now) {
		DriverLinkStatus status = link.statusAt(now);
		return new SentDriverInvitationResponse(
				link.getId(),
				link.getPhone(),
				status,
				status.getLabel(),
				link.getInvitedBy().getUser().getName(),
				link.getCreatedAt(),
				link.expiresAt());
	}
}
