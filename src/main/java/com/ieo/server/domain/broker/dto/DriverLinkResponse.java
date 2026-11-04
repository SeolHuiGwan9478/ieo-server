package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import java.time.Instant;

/**
 * 차주 쪽에서 보는 단골 연결. 받은 요청(PENDING)이면 expiresAt, 연결됨(LINKED)이면 linkedAt이 있다.
 * 차량 앱은 수락 화면에 "○○물류 일을 하는 동안 위치와 도착 예정 시각이 ○○물류에 공유돼요"를 보여 준다.
 */
public record DriverLinkResponse(
		Long id,
		Long brokerId,
		String brokerName,
		DriverLinkStatus status,
		String statusLabel,
		Instant createdAt,
		Instant expiresAt,
		Instant linkedAt) {

	public static DriverLinkResponse from(BrokerDriverLink link, Instant now) {
		DriverLinkStatus status = link.statusAt(now);
		return new DriverLinkResponse(
				link.getId(),
				link.getBroker().getId(),
				link.getBroker().getName(),
				status,
				status.getLabel(),
				link.getCreatedAt(),
				status == DriverLinkStatus.PENDING ? link.expiresAt() : null,
				link.getLinkedAt());
	}
}
