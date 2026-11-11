package com.ieo.server.domain.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LateCancelPenaltyTest {

	private static final Instant DAY0 = Instant.parse("2026-11-01T00:00:00Z");

	private static Instant day(int n) {
		return DAY0.plus(Duration.ofDays(n));
	}

	@Test
	void 삼십일_안에_세_번이면_세_번째부터_7일_막는다() {
		List<Instant> lates = List.of(day(0), day(10), day(20));

		assertThat(LateCancelPenalty.blockedUntil(lates, day(21))).contains(day(27));
		assertThat(LateCancelPenalty.blockedUntil(lates, day(27))).isEmpty();
	}

	@Test
	void 세_번이_30일에_걸쳐_흩어져_있으면_막지_않는다() {
		assertThat(LateCancelPenalty.blockedUntil(List.of(day(0), day(20), day(31)), day(31))).isEmpty();
	}

	@Test
	void 두_번까지는_막지_않는다() {
		assertThat(LateCancelPenalty.blockedUntil(List.of(day(0), day(1)), day(1))).isEmpty();
	}
}
