package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.wait.entity.WaitEvent;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitEventSource;
import com.ieo.server.domain.wait.enums.WaitEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 주선사가 링크로 보는 대기 증빙 (WAIT-020, 025). 운행 하나에 하나이고, 상차지·하차지 대기 기록을 함께 담는다.
 * 로그인 없이 보이므로 차주 이름·전화번호는 넣지 않고 차량 번호만 넣는다.
 * 기사님이 확인하거나 누른 순간은 좌표 대신 상하차지와의 거리로만 보여 준다.
 */
public record WaitProofResponse(
		String plateNumber,
		String brokerName,
		List<Stop> stops,
		Instant issuedAt) {

	/** 통신이 끊겨 이만큼 넘게 늦게 받은 기록은 "나중에 보냄"으로 보여 준다 */
	static final Duration LATE = Duration.ofMinutes(5);

	/** 상하차지 한 곳: 주소, 대기 기록과 계산값, 기사님이 알린 순간들 */
	public record Stop(String siteAddress, WaitRecordResponse record, List<Event> events) {
	}

	/**
	 * @param distanceMeters 그 순간 차와 상하차지 사이 거리(m). 위치를 몰랐으면 null
	 * @param sentLate       통신이 끊겨 나중에 보낸 기록
	 */
	public record Event(WaitEventType type, Instant at, Long distanceMeters, WaitEventSource source, boolean sentLate) {

		static Event of(WaitEvent event, Site site) {
			Long distance = event.latitude() == null || event.longitude() == null ? null
					: Math.round(site.distanceMeters(event.latitude(), event.longitude()));
			boolean late = event.receivedAt() != null && event.at() != null
					&& Duration.between(event.at(), event.receivedAt()).compareTo(LATE) > 0;
			return new Event(event.type(), event.at(), distance, event.source(), late);
		}
	}

	/** [records]는 그 운행의 대기 기록. 상차지 먼저 */
	public static WaitProofResponse of(Trip trip, List<WaitRecord> records, Instant issuedAt) {
		String brokerName = trip.getBroker() == null ? trip.getBrokerName() : trip.getBroker().getName();
		List<Stop> stops = records.stream()
				.sorted(Comparator.comparing(record -> record.getTripStop().getType()))
				.map(record -> {
					Site site = record.getTripStop().getSite();
					return new Stop(site.getAddress(), WaitRecordResponse.from(record),
							record.events().stream().map(event -> Event.of(event, site)).toList());
				})
				.toList();
		return new WaitProofResponse(trip.getVehicle().getPlateNumber(), brokerName, stops, issuedAt);
	}
}
