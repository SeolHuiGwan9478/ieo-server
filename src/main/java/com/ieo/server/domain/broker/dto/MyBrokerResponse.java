package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import java.time.Instant;

/** 내 주선사 회사와 그 안에서의 내 직책. 콘솔은 이걸로 메뉴(대표 전용 설정 등)를 그린다 */
public record MyBrokerResponse(
		Long id,
		String name,
		String businessNumber,
		String licenseNumber,
		String representativeName,
		int settlementDays,
		BrokerMemberPosition position,
		String positionLabel,
		Instant createdAt) {

	public static MyBrokerResponse from(BrokerMember member) {
		Broker broker = member.getBroker();
		return new MyBrokerResponse(
				broker.getId(),
				broker.getName(),
				broker.getBusinessNumber(),
				broker.getLicenseNumber(),
				broker.getRepresentativeName(),
				broker.getSettlementDays(),
				member.getPosition(),
				member.getPosition().getLabel(),
				broker.getCreatedAt());
	}
}
