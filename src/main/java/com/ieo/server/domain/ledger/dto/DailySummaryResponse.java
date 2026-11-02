package com.ieo.server.domain.ledger.dto;

import java.time.LocalDate;

/**
 * 하루 요약 (LEDG-030). 테이블 없이 그날 장부·경비·대기 기록으로 계산한다.
 * netProfit = 운임 + 대기비 − 경비. 대기비는 받았든 아니든 그날 번 돈으로 센다.
 * TODO: 공차 거리·비율은 운행 위치 기록이나 출발 주행거리계 값이 생기면 붙인다
 */
public record DailySummaryResponse(
		LocalDate date,
		int tripCount,
		long fareTotal,
		long waitFeeTotal,
		long expenseTotal,
		long netProfit,
		long waitMinutes,
		long claimableWaitFee) {
}
