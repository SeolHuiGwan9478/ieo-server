package com.ieo.server.domain.delivery.enums;

/** 인수증 상태. 저장하지 않고 사진과 보낸 시각으로 계산한다 */
public enum DeliveryProofStatus {
	/** 아직 사진이 없다 */
	NONE,
	/** 사진을 올렸고 아직 보내지 않았다 */
	PENDING,
	/** 주선사에게 보냈다 */
	SENT
}
