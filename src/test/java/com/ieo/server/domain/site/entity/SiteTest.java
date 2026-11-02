package com.ieo.server.domain.site.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ieo.server.domain.site.enums.SiteAttributeKey;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SiteTest {

	private final Site chilgok = Site.create("칠곡 물류센터", "경북 칠곡군 지천면 칠곡중앙대로 1", 35.9550, 128.5280, null);

	@Test
	void 반경을_정하지_않으면_200m로_만든다() {
		assertThat(chilgok.getGeofenceRadiusM()).isEqualTo(200);
		assertThat(chilgok.getName()).isEqualTo("칠곡 물류센터");
	}

	@Test
	void 좌표나_반경이_범위를_벗어나면_거부한다() {
		assertThatThrownBy(() -> Site.create("a", "b", 91, 128, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_LOCATION);
		assertThatThrownBy(() -> Site.create("a", "b", 35, Double.NaN, null))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_LOCATION);
		assertThatThrownBy(() -> Site.create("a", "b", 35, 128, 30, null))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_RADIUS);
		assertThatThrownBy(() -> Site.create(" ", "b", 35, 128, null))
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	void 두_좌표_사이_거리를_미터로_계산한다() {
		// 위도 0.001도 ≈ 111m
		assertThat(chilgok.distanceMeters(35.9560, 128.5280)).isCloseTo(111.2, within(1.0));
		assertThat(chilgok.isWithin(35.9558, 128.5280, 100)).isTrue();
		assertThat(chilgok.isWithin(35.9560, 128.5280, 100)).isFalse();
	}

	@Test
	void 높이_제한은_소수_둘째_자리로_맞춰_저장한다() {
		SiteAttribute height = SiteAttribute.create(chilgok, SiteAttributeKey.HEIGHT_LIMIT, " 3.5 ", null);

		assertThat(height.getValue()).isEqualTo("3.50");
		assertThat(height.heightLimitM()).isEqualByComparingTo(new BigDecimal("3.5"));
		assertThat(height.flag()).isNull();
	}

	@Test
	void 높이_제한이_숫자가_아니거나_범위를_벗어나면_거부한다() {
		assertThatThrownBy(() -> SiteAttribute.create(chilgok, SiteAttributeKey.HEIGHT_LIMIT, "높음", null))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_ATTRIBUTE);
		assertThatThrownBy(() -> SiteAttribute.create(chilgok, SiteAttributeKey.HEIGHT_LIMIT, "7", null))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_ATTRIBUTE);
	}

	@Test
	void 지게차와_주차는_true나_false만_받는다() {
		assertThat(SiteAttribute.create(chilgok, SiteAttributeKey.FORKLIFT, "TRUE", null).flag()).isTrue();
		assertThat(SiteAttribute.create(chilgok, SiteAttributeKey.PARKING, "false", null).heightLimitM()).isNull();
		assertThatThrownBy(() -> SiteAttribute.create(chilgok, SiteAttributeKey.FORKLIFT, "있음", null))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_ATTRIBUTE);
	}

	@Test
	void 메모는_앞뒤_공백을_지우고_1자에서_500자까지_받는다() {
		SiteNote note = SiteNote.create(chilgok, null, "  지게차 기사 점심시간은 12시부터 1시  ");
		note.markHelpful();
		note.report();

		assertThat(note.getText()).isEqualTo("지게차 기사 점심시간은 12시부터 1시");
		assertThat(note.getHelpfulCount()).isEqualTo(1);
		assertThat(note.getReportedCount()).isEqualTo(1);
		assertThatThrownBy(() -> SiteNote.create(chilgok, null, "   "))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_NOTE);
		assertThatThrownBy(() -> SiteNote.create(chilgok, null, "가".repeat(501)))
				.extracting("errorCode").isEqualTo(ErrorCode.SITE_INVALID_NOTE);
	}
}
