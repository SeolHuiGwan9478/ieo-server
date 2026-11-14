package com.ieo.server.domain.dispatch.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 오더가 마감(CLOSED)된 이유. 마감 탭에서 "주선사가 내린 것"과 "상차 시간이 지나 자동으로 마감된 것"을 구분해 보여 준다.
 * 모집 중·배차 확정 오더는 null
 */
@Getter
@RequiredArgsConstructor
public enum OrderCloseReason {
	/** 주선사가 직접 내림 */
	BROKER("주선사가 내림"),
	/** 상차 가능 시간이 끝나도록 배차되지 않아 서버가 자동으로 마감 (OrderExpirer) */
	EXPIRED("상차 시간 지남"),
	/** 확정된 배차를 취소하면서 다시 모집하지 않고 내림 */
	CANCELED("배차 취소"),
	/** 지정한 단골 차주와 연결이 해제되어 서버가 마감 (BRK-011, 2026-11-13) */
	UNLINKED("단골 해제");

	private final String label;
}
