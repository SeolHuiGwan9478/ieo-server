package com.ieo.server.domain.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TripPositionTest {

	private static final Instant T0 = Instant.parse("2026-11-05T01:00:00Z");

	private final TripPosition position = TripPosition.of(null);

	@Test
	void 처음_받은_남은_거리를_분모로_진행률과_도착_예정을_계산한다() {
		position.update(T0, 36.35, 127.38, 200_000, 9_000, true);
		position.update(T0.plusSeconds(30), 36.36, 127.37, 100_000, 4_500, true);

		assertThat(position.getRouteMeters()).isEqualTo(200_000);
		assertThat(position.progressPercent()).contains(50);
		assertThat(position.eta()).contains(T0.plusSeconds(30 + 4_500));
	}

	@Test
	void 먼저_잰_위치가_늦게_와도_덮어쓰지_않는다() {
		position.update(T0.plusSeconds(60), 36.36, 127.37, 100_000, 4_500, true);

		boolean updated = position.update(T0, 36.35, 127.38, 150_000, 7_000, true);

		assertThat(updated).isFalse();
		assertThat(position.getRemainMeters()).isEqualTo(100_000);
		assertThat(position.getRecordedAt()).isEqualTo(T0.plusSeconds(60));
	}

	@Test
	void 길을_돌아가_남은_거리가_늘면_분모도_늘린다() {
		position.update(T0, 36.35, 127.38, 100_000, 4_000, true);
		position.update(T0.plusSeconds(30), 36.35, 127.38, 120_000, 5_000, true);

		assertThat(position.getRouteMeters()).isEqualTo(120_000);
		assertThat(position.progressPercent()).contains(0);
	}

	@Test
	void 상하차지에_머무는_동안은_남은_거리를_비워_다음_구간에서_분모를_다시_잡는다() {
		position.update(T0, 36.35, 127.38, 30_000, 1_800, true);
		position.update(T0.plusSeconds(60), 36.98, 126.84, 0, 0, false);

		assertThat(position.progressPercent()).isEmpty();
		assertThat(position.eta()).isEmpty();

		position.update(T0.plusSeconds(120), 36.98, 126.84, 250_000, 10_000, true);
		assertThat(position.getRouteMeters()).isEqualTo(250_000);
		assertThat(position.progressPercent()).contains(0);
	}

	@Test
	void 길_안내가_없으면_위치만_남긴다() {
		position.update(T0, 36.35, 127.38, null, null, true);

		assertThat(position.getLat()).isEqualTo(36.35);
		assertThat(position.progressPercent()).isEmpty();
	}
}
