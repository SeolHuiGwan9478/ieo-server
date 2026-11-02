package com.ieo.server.domain.dispatch.enums;

/** 확정 방식 (DSP-005) */
public enum ConfirmMode {
	/** 요청을 받아 주선사가 한 명을 고른다 (기본) */
	SELECT,
	/** 첫 요청을 바로 확정한다 */
	FIRST
}
