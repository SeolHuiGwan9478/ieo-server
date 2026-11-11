package com.ieo.server.domain.wait.enums;

/** 기사님이 그 순간을 어떻게 알렸나. 증빙에서 주선사가 판단하는 근거다 (WAIT-025) */
public enum WaitEventSource {
	/** 앱이 감지하고 물었을 때 창의 버튼으로 확인 */
	CONFIRM_TOUCH,
	/** 앱이 감지하고 물었을 때 말로 확인 */
	CONFIRM_VOICE,
	/** 기사님이 버튼을 직접 누름 */
	BUTTON,
	/** 기사님이 음성 명령으로 직접 ("하차 시작") */
	VOICE,
	/** 기사님 손을 거치지 않고 끝남 (수하인 서명, 운행 완료 처리 등) */
	AUTO
}
