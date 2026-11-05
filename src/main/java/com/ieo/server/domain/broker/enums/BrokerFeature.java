package com.ieo.server.domain.broker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 플랜에 따라 열리는 기능 (12장 PLAN-002). 서버는 API마다 이걸로 검사하고, 콘솔은 목록을 받아 메뉴를 잠근다 */
@Getter
@RequiredArgsConstructor
public enum BrokerFeature {
	/** 운행 데이터 리포트: 노선·상하차지·내 회사 (BRK-040~042) */
	REPORTS("운행 데이터 리포트"),
	/** 함께 일한 차주 이행 기록 (BRK-043) */
	DRIVER_RECORDS("단골 차주 이행 기록"),
	/** 리포트 CSV 내려받기 (BRK-041). 기능은 아직 없다 */
	REPORT_EXPORT("리포트 내려받기"),
	/** 외부 시스템 연동 API. 기능은 아직 없다 */
	API_ACCESS("외부 API 연동");

	private final String label;
}
