package com.ieo.server.domain.trip.entity;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.global.entity.BaseTimeEntity;
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
 * 운행에서 들르는 상하차지 한 곳. 운행마다 상차 1곳, 하차 1곳 (경유 하차는 P3, TRIP-006).
 * 같은 Site에 여러 번 가면 운행마다 TripStop이 따로 생기고, 대기 기록은 여기에 붙는다.
 * 도착·출발 시각은 대기 기록(지오펜스 진입·이탈)이 채운다.
 */
@Getter
@Entity
@Table(name = "trip_stops", uniqueConstraints = @UniqueConstraint(
		name = "uk_trip_stops_trip_type", columnNames = {"trip_id", "stop_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStop extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id")
	private Trip trip;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "stop_type", nullable = false, length = 10)
	private StopType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id")
	private Site site;

	/** 상하차 예정 시각 (선택) */
	private Instant plannedAt;

	/** 지오펜스 진입 시각 */
	private Instant arrivedAt;

	/** 지오펜스 이탈 시각 */
	private Instant departedAt;

	TripStop(Trip trip, StopType type, Site site, Instant plannedAt) {
		this.trip = trip;
		this.type = type;
		this.site = site;
		this.plannedAt = plannedAt;
	}

	/** 처음 진입한 시각만 남긴다. 이탈했다가 다시 들어와도 바꾸지 않는다 */
	public void arrive(Instant now) {
		if (arrivedAt == null) {
			this.arrivedAt = now;
		}
	}

	/** 이탈. 진입 기록이 없으면 이탈 시각을 진입 시각으로도 쓴다 */
	public void depart(Instant now) {
		arrive(now);
		this.departedAt = now;
	}

	public void changePlannedAt(Instant plannedAt) {
		this.plannedAt = plannedAt;
	}

	public boolean isPickup() {
		return type == StopType.PICKUP;
	}
}
