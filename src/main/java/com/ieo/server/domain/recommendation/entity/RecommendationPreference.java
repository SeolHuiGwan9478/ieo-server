package com.ieo.server.domain.recommendation.entity;

import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차주의 추천 설정 (REC-004, REC-005). 차주당 한 줄이고, 없으면 기본값([defaults])으로 본다.
 * 귀가 방향은 뒤로 미뤘다 (docs/design/04_배차추천.md)
 */
@Getter
@Entity
@Table(name = "recommendation_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationPreference {

	public static final int DEFAULT_RADIUS_KM = 30;
	private static final int MIN_RADIUS_KM = 5;
	private static final int MAX_RADIUS_KM = 100;

	@Id
	private Long driverId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	/** 추천 켜기·끄기 */
	@Column(nullable = false)
	private boolean enabled;

	/** 하차지에서 다음 상차지까지 반경(km) */
	@Column(nullable = false)
	private int radiusKm;

	/** 이보다 실수익이 적으면 빼고 안내한다(원). null이면 조건 없음. 프로 플랜에서 쓴다 */
	private Integer minNetProfit;

	/** 추천에서 뺄 화물 종류 (REC-004). 예: 냉동·냉장, 이사 */
	@ElementCollection
	@CollectionTable(name = "recommendation_excluded_cargo", joinColumns = @JoinColumn(name = "driver_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "cargo_category", nullable = false, length = 20)
	private Set<CargoCategory> excludedCargoCategories = EnumSet.noneOf(CargoCategory.class);

	private RecommendationPreference(Driver driver) {
		this.driver = driver;
		this.enabled = true;
		this.radiusKm = DEFAULT_RADIUS_KM;
	}

	/** 아직 설정을 저장하지 않은 차주의 기본값 */
	public static RecommendationPreference defaults(Driver driver) {
		return new RecommendationPreference(driver);
	}

	/** 이 화물 종류를 추천에서 빼는지 */
	public boolean excludes(CargoCategory category) {
		return excludedCargoCategories.contains(category);
	}

	/** excludedCargoCategories가 null이면 제외 화물은 그대로 둔다 */
	public void change(boolean enabled, int radiusKm, Integer minNetProfit, Set<CargoCategory> excludedCargoCategories) {
		if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "추천 반경은 5km에서 100km 사이로 정해 주세요.");
		}
		if (minNetProfit != null && minNetProfit < 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 실수익은 0원 이상으로 정해 주세요.");
		}
		this.enabled = enabled;
		this.radiusKm = radiusKm;
		this.minNetProfit = minNetProfit;
		if (excludedCargoCategories != null) {
			this.excludedCargoCategories.clear();
			this.excludedCargoCategories.addAll(excludedCargoCategories);
		}
	}
}
