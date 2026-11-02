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
 * 어디에 언제 가기로 했는지(계획)만 담는다. 실제로 언제 와서 언제 떠났는지는 대기 기록(WaitRecord)이 가진다.
 * 같은 Site에 여러 번 가면 운행마다 TripStop이 따로 생긴다.
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

	/** 상하차 예정 시각 (선택). 대기비는 이 시각부터 센다 */
	private Instant plannedAt;

	TripStop(Trip trip, StopType type, Site site, Instant plannedAt) {
		this.trip = trip;
		this.type = type;
		this.site = site;
		this.plannedAt = plannedAt;
	}

	/** 대기가 시작된 뒤에 바꾸면 대기비가 달라지므로, 서비스가 대기 기록이 없을 때만 부른다 */
	public void changePlannedAt(Instant plannedAt) {
		this.plannedAt = plannedAt;
	}

	public boolean isPickup() {
		return type == StopType.PICKUP;
	}
}
