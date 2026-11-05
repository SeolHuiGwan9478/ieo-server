package com.ieo.server.domain.broker.dto.report;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 리포트 기간 (한국 날짜, 양 끝 포함). 비우면 오늘까지 최근 90일.
 * start·end는 조회용 UTC 시각 (from 0시 ~ to 다음날 0시 직전)
 */
public record ReportPeriod(LocalDate from, LocalDate to, Instant start, Instant end) {

	public static final int DEFAULT_DAYS = 90;
	public static final int MAX_DAYS = 366;

	public static ReportPeriod of(LocalDate from, LocalDate to, Clock clock) {
		LocalDate end = to == null ? KoreanDate.today(clock) : to;
		LocalDate begin = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
		if (begin.isAfter(end)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦어요.");
		}
		if (ChronoUnit.DAYS.between(begin, end) >= MAX_DAYS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "기간은 1년까지 고를 수 있어요.");
		}
		return new ReportPeriod(begin, end,
				begin.atStartOfDay(KoreanDate.ZONE).toInstant(),
				end.plusDays(1).atStartOfDay(KoreanDate.ZONE).toInstant().minusNanos(1));
	}
}
