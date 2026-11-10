package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import com.ieo.server.domain.document.service.DocumentReview;
import java.time.Instant;

/**
 * 주선사 심사 목록의 한 줄 = 회사 하나 (07 인계서 13장). 서류 진행은 "승인된 필수 서류 / 필수 서류 수".
 * business*는 사업자등록증이 심사 대기일 때만 채운다
 */
public record AdminBrokerReviewRow(
		Long brokerId,
		String name,
		String businessNumber,
		String licenseNumber,
		String representativeName,
		int approvedRequired,
		int requiredTotal,
		long pendingCount,
		long rejectedCount,
		boolean verified,
		Instant latestSubmittedAt,
		Instant oldestPendingAt,
		boolean resubmitted,
		BusinessCheckResult businessCheckResult,
		BusinessStatus businessStatus,
		boolean businessAttention
) {

	public static AdminBrokerReviewRow of(Broker broker, DocumentReview<?, ?> review) {
		return new AdminBrokerReviewRow(
				broker.getId(),
				broker.getName(),
				BusinessCheck.format(broker.getBusinessNumber()),
				broker.getLicenseNumber(),
				broker.getRepresentativeName(),
				review.approvedRequired(),
				review.requiredTotal(),
				review.pendingCount(),
				review.rejectedCount(),
				review.verified(),
				review.latestSubmittedAt().orElse(null),
				review.oldestPendingAt().orElse(null),
				review.resubmitted(),
				review.pendingBusinessCheck().map(BusinessCheck::getResult).orElse(null),
				review.pendingBusinessCheck().map(BusinessCheck::getStatus).orElse(null),
				review.pendingBusinessCheck().map(BusinessCheck::needsAttention).orElse(false));
	}
}
