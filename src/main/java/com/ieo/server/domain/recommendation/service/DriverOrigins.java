package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.global.geo.GeoDistance;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 빈 차 거리를 어디서부터 잴지 정한다 (REC-001, DSP-011, 2026-11-13).
 * 상차지·하차지에 도착해 있으면 그 상하차지 좌표, 아니면 기사님이 보낸 지금 위치.
 * 상하차지 좌표는 늘 같아서 같은 곳에서 다시 물을 때 TMAP 결과를 다시 쓸 수 있다
 */
@Component
@RequiredArgsConstructor
public class DriverOrigins {

	private final TripRepository tripRepository;

	public DriverOrigin of(Long userId, double lat, double lng) {
		Optional<Trip> current = tripRepository.findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(userId, TripStatus.IN_PROGRESS);
		if (current.isEmpty()) {
			return new DriverOrigin(null, null, null, lat, lng, false);
		}
		Trip trip = current.get();
		return switch (trip.getStatus()) {
			case AT_PICKUP -> atStop(trip, StopType.PICKUP, trip.pickup().getSite());
			case AT_DROPOFF -> atStop(trip, StopType.DROPOFF, trip.dropoff().getSite());
			default -> new DriverOrigin(null, null, null, lat, lng, true);
		};
	}

	private static DriverOrigin atStop(Trip trip, StopType type, Site site) {
		return new DriverOrigin(trip.getId(), type, site.getName(), site.getLat(), site.getLng(), false);
	}

	/**
	 * @param tripId   상하차지에 있으면 그 운행, 아니면 null
	 * @param stopType 상차지인지 하차지인지, 아니면 null
	 * @param siteName 상하차지 이름. 지금 위치에서 잴 때는 null
	 * @param driving  상차지로 가는 중이거나 운송 중
	 */
	public record DriverOrigin(Long tripId, StopType stopType, String siteName, double lat, double lng, boolean driving) {

		public boolean atStop() {
			return tripId != null;
		}

		public boolean isNear(double otherLat, double otherLng, double meters) {
			return GeoDistance.approxMeters(lat, lng, otherLat, otherLng) <= meters;
		}
	}
}
