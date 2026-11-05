package com.ieo.server.domain.recommendation.entity;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.recommendation.enums.RecommendationAnswer;
import com.ieo.server.domain.trip.entity.Trip;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주에게 실제로 안내한 추천 한 건. 후보 목록은 저장하지 않고 부를 때마다 계산한다.
 * 실수익·공차 거리는 다시 계산할 수 있어 저장하지 않는다 (docs/design/04_배차추천.md)
 */
@Getter
@Entity
@Table(name = "recommendations",
		uniqueConstraints = @UniqueConstraint(name = "uk_recommendations_trip_order", columnNames = {"trip_id", "order_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 안내할 때 진행 중이던 운행 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id")
	private Trip trip;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@Column(nullable = false)
	private Instant presentedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private RecommendationAnswer answer;

	private Recommendation(Trip trip, Order order, Instant presentedAt) {
		this.trip = trip;
		this.order = order;
		this.presentedAt = presentedAt;
	}

	public static Recommendation presented(Trip trip, Order order, Instant now) {
		return new Recommendation(trip, order, now);
	}

	/** 대답을 적는다. null이면 안내만 하고 대답이 없었던 것 */
	public void answer(RecommendationAnswer answer) {
		this.answer = answer;
	}
}
