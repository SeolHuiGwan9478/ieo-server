package com.ieo.server.domain.vehicle.dto;

import java.util.List;

/**
 * vehicle: 이 트럭으로 운행을 시작할 차량. LINKED면 연결된 차량, OTHER_OWNER면 내 기본 차량(없으면 null).
 * autoLinked: 차량이 한 대뿐이라 묻지 않고 방금 연결했으면 true. 앱은 "OO으로 시작할게요"를 보여 주고 바꿀 수 있게 한다.
 * candidates: SELECT일 때 고를 내 차량 목록.
 */
public record PleosResolveResponse(
		PleosResolveStatus status,
		boolean autoLinked,
		VehicleResponse vehicle,
		List<VehicleResponse> candidates) {
}
