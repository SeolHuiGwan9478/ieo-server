package com.ieo.server.global.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 시각은 UTC(Instant)로 저장하고, 장부의 "그날"처럼 날짜가 필요할 때는 한국 날짜로 바꾼다 */
public final class KoreanDate {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private KoreanDate() {
	}

	public static LocalDate of(Instant instant) {
		return instant.atZone(ZONE).toLocalDate();
	}

	public static LocalDate today(Clock clock) {
		return of(clock.instant());
	}
}
