package com.ieo.server.domain.broker.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 대시보드 그래프의 한 칸. from을 비우면 unit마다 기본 기간이 다르다 (일은 30일, 주는 12주, 월은 6개월) */
@Getter
@RequiredArgsConstructor
public enum DashboardUnit {

	DAY("일", 30),
	WEEK("주", 7 * 12),
	MONTH("월", 0);

	private final String label;
	/** 기본 기간 (끝날 포함 일수). MONTH는 그 달 포함 6개월이라 따로 센다 */
	private final int defaultDays;

	/** 기본 시작일 */
	public LocalDate defaultFrom(LocalDate end) {
		return this == MONTH ? end.withDayOfMonth(1).minusMonths(5) : end.minusDays(defaultDays - 1);
	}

	/** 날짜가 든 칸의 첫날 (주는 월요일, 월은 1일) */
	public LocalDate bucketStart(LocalDate date) {
		return switch (this) {
			case DAY -> date;
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTH -> date.withDayOfMonth(1);
		};
	}

	/** 다음 칸의 첫날 */
	public LocalDate next(LocalDate bucketStart) {
		return switch (this) {
			case DAY -> bucketStart.plusDays(1);
			case WEEK -> bucketStart.plusWeeks(1);
			case MONTH -> bucketStart.plusMonths(1);
		};
	}
}
