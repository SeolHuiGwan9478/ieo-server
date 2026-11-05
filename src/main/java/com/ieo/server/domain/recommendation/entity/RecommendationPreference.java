package com.ieo.server.domain.recommendation.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차주의 추천 설정 (REC-004, REC-025). 차주당 한 줄이고, 없으면 기본값([defaults])으로 본다.
 * 제외 화물·귀가 방향은 뒤로 미뤘다 (docs/design/04_배차추천.md)
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

	private RecommendationPreference(Driver driver) {
		this.driver = driver;
		this.enabled = true;
		this.radiusKm = DEFAULT_RADIUS_KM;
	}

	/** 아직 설정을 저장하지 않은 차주의 기본값 */
	public static RecommendationPreference defaults(Driver driver) {
		return new RecommendationPreference(driver);
	}

	public void change(boolean enabled, int radiusKm, Integer minNetProfit) {
		if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "추천 반경은 5km에서 100km 사이로 정해 주세요.");
		}
		if (minNetProfit != null && minNetProfit < 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 실수익은 0원 이상으로 정해 주세요.");
		}
		this.enabled = enabled;
		this.radiusKm = radiusKm;
		this.minNetProfit = minNetProfit;
	}
}
