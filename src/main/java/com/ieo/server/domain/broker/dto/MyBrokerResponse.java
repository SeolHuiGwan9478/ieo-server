package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import java.math.BigDecimal;
import java.time.Instant;

/** 내 주선사 회사와 그 안에서의 내 직책, 플랜, 서류 인증 여부. 콘솔은 이걸로 메뉴(대표 전용 설정, 플랜별 기능)와 오더 등록 가능 여부를 그린다 */
public record MyBrokerResponse(
		Long id,
		String name,
		String businessNumber,
		String licenseNumber,
		String representativeName,
		int settlementDays,
		BigDecimal defaultFeeRate,
		BrokerMemberPosition position,
		String positionLabel,
		BrokerPlanResponse plan,
		/** 회사 서류가 모두 승인됐는지. 아니면 오더를 올릴 수 없다 (07 인계서 13장) */
		boolean verified,
		Instant createdAt) {

	public static MyBrokerResponse from(BrokerMember member, BrokerPlanResponse plan, boolean verified) {
		Broker broker = member.getBroker();
		return new MyBrokerResponse(
				broker.getId(),
				broker.getName(),
				broker.getBusinessNumber(),
				broker.getLicenseNumber(),
				broker.getRepresentativeName(),
				broker.getSettlementDays(),
				broker.getDefaultFeeRate(),
				member.getPosition(),
				member.getPosition().getLabel(),
				plan,
				verified,
				broker.getCreatedAt());
	}
}
