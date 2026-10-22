package com.ieo.server.domain.broker.enums;

public enum BrokerMemberPosition {
	/** 회사를 처음 등록한 대표 담당자. 다른 담당자를 초대하고 회사 정보를 바꿀 수 있다 */
	OWNER,
	/** 초대받은 담당자 */
	STAFF
}
