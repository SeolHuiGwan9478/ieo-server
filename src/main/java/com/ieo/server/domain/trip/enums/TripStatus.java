package com.ieo.server.domain.trip.enums;

import java.util.Set;

/**
 * 운행 상태 (TRIP-010). 정해진 순서로만 넘어가고, 완료 전에는 어디서든 취소할 수 있다.
 * SCHEDULED → TO_PICKUP → AT_PICKUP → IN_TRANSIT → AT_DROPOFF → COMPLETED
 */
public enum TripStatus {
	/** 예약됨 (등록만 함) */
	SCHEDULED,
	/** 상차지로 이동 중 */
	TO_PICKUP,
	/** 상차지에서 대기·상차 중 */
	AT_PICKUP,
	/** 운송 중 */
	IN_TRANSIT,
	/** 하차지에서 대기·하차 중 */
	AT_DROPOFF,
	COMPLETED,
	CANCELLED;

	/** 아직 끝나지 않은 운행 */
	public static final Set<TripStatus> ACTIVE = Set.of(SCHEDULED, TO_PICKUP, AT_PICKUP, IN_TRANSIT, AT_DROPOFF);

	/** 출발해서 아직 끝나지 않은 운행. 차주당 하나만 있을 수 있다 */
	public static final Set<TripStatus> IN_PROGRESS = Set.of(TO_PICKUP, AT_PICKUP, IN_TRANSIT, AT_DROPOFF);

	public boolean canMoveTo(TripStatus next) {
		if (next == CANCELLED) {
			return this != COMPLETED && this != CANCELLED;
		}
		return switch (this) {
			case SCHEDULED -> next == TO_PICKUP;
			case TO_PICKUP -> next == AT_PICKUP;
			case AT_PICKUP -> next == IN_TRANSIT;
			case IN_TRANSIT -> next == AT_DROPOFF;
			case AT_DROPOFF -> next == COMPLETED;
			case COMPLETED, CANCELLED -> false;
		};
	}

	public boolean isFinished() {
		return this == COMPLETED || this == CANCELLED;
	}
}
