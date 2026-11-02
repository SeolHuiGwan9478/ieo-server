package com.ieo.server.domain.vehicle.dto;

/** 차량에서 로그인한 뒤 이 트럭이 내 차량 중 어느 것인지 찾은 결과 (VEH-004) */
public enum PleosResolveStatus {
	/** 이 트럭에 연결된 내 차량이 있음 (차량이 한 대뿐이라 방금 자동으로 연결한 경우 포함) */
	LINKED,
	/** 내 차량이 여러 대인데 이 트럭은 아직 연결 안 됨. candidates 중에서 고르게 한다 */
	SELECT,
	/** 등록된 차량이 없음. 등록 화면을 띄운다 */
	REGISTER,
	/** 이 트럭은 다른 차주의 차량에 연결돼 있음. 안내만 하고 연결하지 않는다 */
	OTHER_OWNER
}
