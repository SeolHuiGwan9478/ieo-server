package com.ieo.server.domain.site.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HeightWarningTest {

	@Test
	void 차가_높이_제한_이상이면_진입_불가() {
		assertThat(level("3.60", "3.50")).isEqualTo(HeightWarningLevel.BLOCKED);
		assertThat(level("3.50", "3.50")).isEqualTo(HeightWarningLevel.BLOCKED);
	}

	@Test
	void 여유가_20cm_미만이면_주의_이상이면_괜찮음() {
		assertThat(level("3.35", "3.50")).isEqualTo(HeightWarningLevel.CAUTION);
		assertThat(level("3.30", "3.50")).isEqualTo(HeightWarningLevel.OK);
	}

	@Test
	void 차_높이나_높이_제한을_모르면_UNKNOWN() {
		assertThat(HeightWarning.of(null, new BigDecimal("3.5")).level()).isEqualTo(HeightWarningLevel.UNKNOWN);
		assertThat(HeightWarning.of(new BigDecimal("3.5"), null).level()).isEqualTo(HeightWarningLevel.UNKNOWN);
	}

	private HeightWarningLevel level(String vehicle, String limit) {
		return HeightWarning.of(new BigDecimal(vehicle), new BigDecimal(limit)).level();
	}
}
