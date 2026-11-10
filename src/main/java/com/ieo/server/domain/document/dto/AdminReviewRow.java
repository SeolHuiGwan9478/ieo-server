package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.enums.BusinessCheckResult;
import com.ieo.server.domain.document.enums.BusinessStatus;
import com.ieo.server.domain.document.service.DocumentReview;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.time.Instant;

/**
 * 심사 목록의 한 줄 = 차주 한 명 (07 인계서 6.2). 서류 진행은 "승인된 필수 서류 / 필수 서류 수".
 * business*는 사업자등록증이 심사 대기일 때만 채운다 (국세청 조회 결과·사업자 상태, 눈여겨볼 건인지)
 */
public record AdminReviewRow(
		Long driverId,
		String name,
		String phone,
		String plateNumber,
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

	public static AdminReviewRow of(User user, Vehicle vehicle, DocumentReview<?, ?> review) {
		return new AdminReviewRow(
				user.getId(),
				user.getName(),
				PhoneMask.mask(user.getPhone()),
				vehicle == null ? null : vehicle.getPlateNumber(),
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
