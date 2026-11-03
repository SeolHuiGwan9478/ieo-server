package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import java.time.Instant;

/** 우리 회사 담당자. me는 요청한 본인인지 (콘솔에서 "나" 표시와 나가기 버튼에 쓴다) */
public record BrokerMemberResponse(
		Long userId,
		String name,
		String phone,
		BrokerMemberPosition position,
		String positionLabel,
		boolean me,
		Instant joinedAt) {

	public static BrokerMemberResponse from(BrokerMember member, Long requesterId) {
		return new BrokerMemberResponse(
				member.getUserId(),
				member.getUser().getName(),
				member.getUser().getPhone(),
				member.getPosition(),
				member.getPosition().getLabel(),
				member.getUserId().equals(requesterId),
				member.getCreatedAt());
	}
}
