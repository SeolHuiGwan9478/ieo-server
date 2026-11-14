package com.ieo.server.domain.plan.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 기사님 플랜으로 열리는 기능. 서버가 API마다 검사하고, 앱은 목록을 받아 잠금 안내를 그린다 (PLAN-002) */
@Getter
@RequiredArgsConstructor
public enum DriverFeature {
	/** 상하차지에 세우면 근처 실수익 1등 오더를 추천 (REC-001). 추천 TMAP 원가가 커서 프로만 (2026-11-14) */
	RECOMMENDATION("배차 추천"),
	/** 이번 달보다 앞선 장부 보기 (LEDG) */
	LEDGER_HISTORY("지난 장부 보기"),
	/** 장부 엑셀·PDF 내보내기 (LEDG-033, 034) */
	LEDGER_EXPORT("장부 내보내기"),
	/** 운행이 끝나면 세금계산서 자동 발행 (LEDG-041). 자동 발행 자체는 아직 없다 */
	TAX_INVOICE_AUTO("세금계산서 자동 발행");

	private final String label;
}
