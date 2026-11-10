package com.ieo.server.domain.user.enums;

public enum UserRole {
	/** 화물차 기사 */
	DRIVER,
	/** 운송주선업체 담당자 */
	BROKER,
	/** 이에오 운영자. 가입으로는 만들 수 없고 서버에서 직접 지정한다 (scripts/seed-documents.py) */
	ADMIN
}
