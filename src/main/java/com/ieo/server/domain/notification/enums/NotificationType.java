package com.ieo.server.domain.notification.enums;

/** 차주에게 보내는 앱 알림 종류 (09 알림 "배차 확정·거절·만료") */
public enum NotificationType {
	/** 주선사가 확정했다. 내 배차에 예약 운행이 생겼다 */
	DISPATCH_CONFIRMED,
	/** 다른 차주로 마감됐거나 주선사가 오더를 내렸다 */
	DISPATCH_DECLINED,
	/** 10분 안에 확정되지 않았다 */
	DISPATCH_EXPIRED
}
