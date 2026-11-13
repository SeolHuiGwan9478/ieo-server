package com.ieo.server.domain.recommendation.entity;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.trip.enums.StopType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 한 번 (REC-001, 2026-11-13). 같은 운행의 같은 상하차지에서는 이 기록을 다시 돌려줘 TMAP을 다시 부르지 않고 큰 창도 다시 띄우지 않는다.
 * 운행이 없을 때는 10분 안, 1km 안이면 다시 쓴다. 추천할 오더가 없었으면 order는 비어 있다(그때도 다시 계산하지 않는다).
 * 실수익 숫자는 보여 준 그대로 적어 두고, 다시 돌려줄 때 그 숫자를 쓴다
 */
@Getter
@Entity
@Table(name = "recommendation_shown")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationShown {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	/** 상하차지에서 받은 추천이면 그 운행. 운행이 없을 때는 비어 있다 */
	private Long tripId;

	/** 상차지인지 하차지인지. 운행이 없을 때는 비어 있다 */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private StopType stopType;

	/** 빈 차 거리를 잰 출발점: 상하차지 좌표, 운행이 없으면 세운 자리 */
	@Column(nullable = false)
	private double originLat;

	@Column(nullable = false)
	private double originLng;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Order order;

	private Integer netProfit;

	private Integer fuelCost;

	private Integer tollFare;

	private Long emptyMeters;

	@Column(nullable = false)
	private Instant shownAt;

	private RecommendationShown(Driver driver, Long tripId, StopType stopType, double originLat, double originLng, Instant shownAt) {
		this.driver = driver;
		this.tripId = tripId;
		this.stopType = stopType;
		this.originLat = originLat;
		this.originLng = originLng;
		this.shownAt = shownAt;
	}

	/** 추천할 오더가 없었다 */
	public static RecommendationShown nothing(Driver driver, Long tripId, StopType stopType, double originLat, double originLng,
			Instant shownAt) {
		return new RecommendationShown(driver, tripId, stopType, originLat, originLng, shownAt);
	}

	public static RecommendationShown of(Driver driver, Long tripId, StopType stopType, double originLat, double originLng,
			Order order, int netProfit, int fuelCost, Integer tollFare, long emptyMeters, Instant shownAt) {
		RecommendationShown shown = new RecommendationShown(driver, tripId, stopType, originLat, originLng, shownAt);
		shown.order = order;
		shown.netProfit = netProfit;
		shown.fuelCost = fuelCost;
		shown.tollFare = tollFare;
		shown.emptyMeters = emptyMeters;
		return shown;
	}

	/** 출발점에서 (lat, lng)까지 [meters] 안인지. 짧은 거리라 평면으로 어림한다 */
	public boolean isWithin(double lat, double lng, double meters) {
		double dy = (lat - originLat) * 111_320;
		double dx = (lng - originLng) * 111_320 * Math.cos(Math.toRadians(originLat));
		return Math.hypot(dx, dy) <= meters;
	}
}
