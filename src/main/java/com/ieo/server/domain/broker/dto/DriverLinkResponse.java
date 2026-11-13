package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import java.time.Instant;

/**
 * 차주 쪽에서 보는 단골 연결. 받은 요청(PENDING)이면 expiresAt, 연결됨(LINKED)이면 linkedAt이 있다.
 * 휴대폰이 수락·거절을 고를 수 있게 그 회사의 신뢰 지표(brokerMetrics)와 함께 끝낸 운행 수(tripsTogether)를 붙인다.
 * 수락·거절·해제는 휴대폰에서만 한다 (2026-11-13).
 */
public record DriverLinkResponse(
		Long id,
		Long brokerId,
		String brokerName,
		DriverLinkStatus status,
		String statusLabel,
		Instant createdAt,
		Instant expiresAt,
		Instant linkedAt,
		BrokerMetricsResponse brokerMetrics,
		long tripsTogether) {

	public static DriverLinkResponse from(BrokerDriverLink link, Instant now) {
		return from(link, now, null, 0);
	}

	public static DriverLinkResponse from(BrokerDriverLink link, Instant now, BrokerMetricsResponse metrics, long tripsTogether) {
		DriverLinkStatus status = link.statusAt(now);
		return new DriverLinkResponse(
				link.getId(),
				link.getBroker().getId(),
				link.getBroker().getName(),
				status,
				status.getLabel(),
				link.getCreatedAt(),
				status == DriverLinkStatus.PENDING ? link.expiresAt() : null,
				link.getLinkedAt(),
				metrics,
				tripsTogether);
	}
}
