package com.ieo.server.domain.delivery.enums;

/** 인수증 상태. 저장하지 않고 서명·사진·전달 시각으로 계산한다 */
public enum DeliveryProofStatus {
	/** 링크 발급, 서명 전 */
	PENDING,
	/** 수하인이 서명함 */
	SIGNED,
	/** 서명 대신 차주가 종이 인수증 사진을 올림 (POD-009) */
	PHOTO,
	/** 주선사에게 전달됨 */
	DELIVERED
}
