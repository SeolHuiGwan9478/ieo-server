package com.ieo.server.domain.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운행 중인 차의 마지막 위치 (TRIP-011, 013). 운행마다 한 줄만 두고 새 위치가 오면 덮어쓴다.
 * 지나온 경로는 남기지 않는다. 운행이 끝나면 지운다.
 *
 * 진행률은 "이번 구간(상차지까지 또는 하차지까지)의 처음 남은 거리" 대비 지금 남은 거리로 계산한다.
 * 상하차지에 머무는 동안에는 남은 거리를 비워서, 다음 구간이 시작되면 처음 거리를 다시 잡는다.
 */
@Getter
@Entity
@Table(name = "trip_positions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPosition {

	@Id
	private Long tripId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id")
	private Trip trip;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lng;

	/** 차에서 위치를 잰 시각. 늦게 도착한 옛 위치로 덮어쓰지 않으려고 비교한다 */
	@Column(nullable = false)
	private Instant recordedAt;

	/** 길 안내에서 읽은 남은 거리(m)와 시간(초). 길 안내 중이 아니면 비어 있다 */
	private Integer remainMeters;

	private Integer remainSeconds;

	/** 이번 구간의 처음 남은 거리(m). 진행률의 분모 */
	private Integer routeMeters;

	private TripPosition(Trip trip) {
		this.trip = trip;
	}

	public static TripPosition of(Trip trip) {
		return new TripPosition(trip);
	}

	/**
	 * 새 위치로 덮어쓴다. 지금 가진 위치보다 오래된 것이면 무시하고 false.
	 * moving이 false면(상하차지에 머무는 중) 남은 거리를 비운다
	 */
	public boolean update(Instant recordedAt, double lat, double lng,
			Integer remainMeters, Integer remainSeconds, boolean moving) {
		if (this.recordedAt != null && !recordedAt.isAfter(this.recordedAt)) {
			return false;
		}
		this.recordedAt = recordedAt;
		this.lat = lat;
		this.lng = lng;
		if (!moving || remainMeters == null) {
			this.remainMeters = null;
			this.remainSeconds = null;
			if (!moving) {
				this.routeMeters = null;
			}
			return true;
		}
		this.remainMeters = remainMeters;
		this.remainSeconds = remainSeconds;
		// 처음 받은 남은 거리가 분모. 길을 돌아가서 남은 거리가 늘면 분모도 늘린다
		this.routeMeters = routeMeters == null ? remainMeters : Math.max(routeMeters, remainMeters);
		return true;
	}

	/** 이번 구간 진행률 0~100. 길 안내 중이 아니면 비어 있다 */
	public Optional<Integer> progressPercent() {
		if (remainMeters == null || routeMeters == null || routeMeters == 0) {
			return Optional.empty();
		}
		return Optional.of((int) Math.floor(100.0 * (routeMeters - remainMeters) / routeMeters));
	}

	/** 도착 예정 시각 = 위치를 잰 시각 + 남은 시간 */
	public Optional<Instant> eta() {
		return Optional.ofNullable(remainSeconds).map(recordedAt::plusSeconds);
	}
}
