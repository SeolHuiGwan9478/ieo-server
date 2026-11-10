package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.document.entity.BusinessCheck;
import java.time.Instant;
import java.util.List;

/**
 * 회사별 심사 상세 (07 인계서 13장). 서류 사진과 나란히 대조할 회사 등록 정보(상호·사업자번호·허가 번호·대표자),
 * 서류별 최신 상태와 이전 제출, 처리 이력, 주선사용 반려 사유 목록을 한 번에 준다
 */
public record AdminBrokerReviewResponse(
		BrokerInfo broker,
		boolean verified,
		int approvedRequired,
		int requiredTotal,
		List<DocumentTypeResponse> documents,
		List<EventResponse> events,
		List<RejectReasonResponse> rejectReasons
) {

	/** 대표 담당자의 이름과 가린 휴대폰은 운영자가 연락할 때 쓴다 */
	public record BrokerInfo(
			Long id,
			String name,
			String businessNumber,
			String licenseNumber,
			String representativeName,
			Instant registeredAt,
			int memberCount,
			String ownerName,
			String ownerPhone
	) {

		public static BrokerInfo from(Broker broker, List<BrokerMember> activeMembers) {
			BrokerMember owner = activeMembers.stream().filter(BrokerMember::isOwner).findFirst().orElse(null);
			return new BrokerInfo(broker.getId(), broker.getName(), BusinessCheck.format(broker.getBusinessNumber()), broker.getLicenseNumber(),
					broker.getRepresentativeName(), broker.getCreatedAt(), activeMembers.size(),
					owner == null ? null : owner.getUser().getName(),
					owner == null ? null : PhoneMask.mask(owner.getUser().getPhone()));
		}
	}
}
