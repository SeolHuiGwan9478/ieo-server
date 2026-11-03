package com.ieo.server.domain.broker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 담당자 초대 상태. EXPIRED는 저장하지 않고 시각으로 판단한다 (초대 후 7일)
 */
@Getter
@RequiredArgsConstructor
public enum BrokerInvitationStatus {
	/** 수락을 기다리는 중 */
	PENDING("대기 중"),
	/** 수락해서 담당자가 됨 */
	ACCEPTED("수락"),
	/** 초대받은 사람이 거절 */
	DECLINED("거절"),
	/** 대표가 취소 */
	CANCELED("취소"),
	/** 7일 안에 수락하지 않음 */
	EXPIRED("만료");

	private final String label;
}
