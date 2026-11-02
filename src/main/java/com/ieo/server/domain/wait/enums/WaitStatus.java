package com.ieo.server.domain.wait.enums;

/** 대기 기록 상태. 저장하지 않고 시각으로 계산한다 */
public enum WaitStatus {
	/** 진입했고 작업 시작 전 */
	WAITING,
	/** 작업 시작했고 이탈 전 */
	WORKING,
	/** 이탈 */
	DONE
}
