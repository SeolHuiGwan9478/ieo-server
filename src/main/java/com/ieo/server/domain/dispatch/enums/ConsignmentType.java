package com.ieo.server.domain.dispatch.enums;

/** 위탁 관계 (DSP-003). 재위탁 오더는 다단계 경고 대상이다 */
public enum ConsignmentType {
	/** 화주에게 직접 받은 오더 */
	DIRECT,
	/** 다른 운송사·주선사에게 받은 오더 */
	SUBCONTRACT
}
