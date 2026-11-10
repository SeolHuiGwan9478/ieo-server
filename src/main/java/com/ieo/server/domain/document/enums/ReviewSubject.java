package com.ieo.server.domain.document.enums;

/** 서류를 내는 쪽. 반려 사유 목록이 이걸로 갈린다 (차량 번호 불일치는 차주만, 허가 번호 불일치는 주선사만) */
public enum ReviewSubject {
	DRIVER,
	BROKER
}
