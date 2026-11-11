package com.ieo.server.domain.wait.enums;

/** 대기 기록에 남기는 순간 (WAIT-025) */
public enum WaitEventType {
	/** 대기 시작 (도착 확인 또는 "대기 시작") */
	ARRIVED,
	/** 작업 시작 ("상차 시작"·"하차 시작") */
	WORK_STARTED,
	/** 떠남 (떠남 확인, "운행 완료"·"상차 끝, 출발") */
	DEPARTED
}
