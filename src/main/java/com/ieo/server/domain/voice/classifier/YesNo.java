package com.ieo.server.domain.voice.classifier;

/** 예/아니오로 물은 질문에 대한 차주 대답의 뜻 */
public enum YesNo {
	YES,
	NO,
	/** 질문과 관계없거나 뜻을 알 수 없다. 앱이 다시 묻는다 */
	UNKNOWN
}
