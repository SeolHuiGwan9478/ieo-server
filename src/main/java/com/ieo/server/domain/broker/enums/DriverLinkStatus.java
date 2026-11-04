package com.ieo.server.domain.broker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 단골 차주 연결 상태 (BRK-002). PENDING → LINKED/DECLINED/CANCELED, LINKED → UNLINKED.
 * EXPIRED는 저장하지 않고 시각으로 판단한다 (요청 후 7일)
 */
@Getter
@RequiredArgsConstructor
public enum DriverLinkStatus {
	/** 차주의 수락을 기다리는 중 */
	PENDING("대기 중"),
	/** 차주가 수락해서 단골로 연결됨 */
	LINKED("연결됨"),
	/** 차주가 거절 */
	DECLINED("거절"),
	/** 주선사가 요청을 취소 */
	CANCELED("취소"),
	/** 연결된 뒤 주선사나 차주가 해제 */
	UNLINKED("해제"),
	/** 7일 안에 수락하지 않음 */
	EXPIRED("만료");

	private final String label;
}
