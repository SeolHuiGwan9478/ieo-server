package com.ieo.server.domain.broker.enums;

import com.ieo.server.domain.trip.enums.TripStatus;
import java.util.Set;

/** 주선사 콘솔 운행 목록의 탭 (BRK-020). 기본은 진행 중 */
public enum BrokerTripTab {
	/** 출발해서 하차 완료 전 */
	IN_PROGRESS(TripStatus.IN_PROGRESS),
	/** 배차는 확정됐고 아직 출발 전 */
	SCHEDULED(Set.of(TripStatus.SCHEDULED)),
	/** 끝난 운행 (완료·취소). 인수증·대기 증빙을 다시 찾을 때 */
	FINISHED(Set.of(TripStatus.COMPLETED, TripStatus.CANCELLED));

	private final Set<TripStatus> statuses;

	BrokerTripTab(Set<TripStatus> statuses) {
		this.statuses = statuses;
	}

	public Set<TripStatus> statuses() {
		return statuses;
	}
}
