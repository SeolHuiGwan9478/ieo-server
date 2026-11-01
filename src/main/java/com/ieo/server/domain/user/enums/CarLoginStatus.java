package com.ieo.server.domain.user.enums;

/**
 * 차량 QR 로그인 세션 상태.
 * EXPIRED는 저장하지 않고 시각으로 판단한다. 만료됐다고 행을 고치러 다닐 필요가 없게.
 */
public enum CarLoginStatus {
	/** 휴대폰 승인을 기다리는 중 */
	PENDING,
	/** 휴대폰이 승인함. 차량이 아직 토큰을 받아 가지 않음 */
	APPROVED,
	/** 휴대폰이 거절함 */
	REJECTED,
	/** 차량이 토큰을 받아 감. 같은 세션으로 다시 받을 수 없다 */
	CONSUMED,
	/** 승인 없이 5분이 지났거나, 승인된 뒤 1분 안에 받아 가지 않음 */
	EXPIRED
}
