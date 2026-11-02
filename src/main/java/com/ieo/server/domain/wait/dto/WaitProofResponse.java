package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.wait.entity.WaitRecord;
import java.time.Instant;

/**
 * 주선사가 링크로 보는 대기 증빙 (WAIT-020). 로그인 없이 보이므로 차주 이름·전화번호는 넣지 않고 차량 번호만 넣는다.
 * TODO: 위치 이력 지도는 운행 위치 기록(LocationPoint)을 만든 뒤 붙인다
 */
public record WaitProofResponse(
		String siteAddress,
		String plateNumber,
		String brokerName,
		WaitRecordResponse record,
		Instant issuedAt) {

	public static WaitProofResponse of(WaitRecord record, Instant issuedAt) {
		Site site = record.getTripStop().getSite();
		Trip trip = record.getTripStop().getTrip();
		String brokerName = trip.getBroker() == null ? trip.getBrokerName() : trip.getBroker().getName();
		return new WaitProofResponse(site.getAddress(), trip.getVehicle().getPlateNumber(), brokerName,
				WaitRecordResponse.from(record), issuedAt);
	}
}
