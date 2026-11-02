package com.ieo.server.domain.site.dto;

/** 내 차 높이와 상하차지 높이 제한 비교 결과 (VEH-002) */
public enum HeightWarningLevel {
	/** 차가 높이 제한 이상. 진입할 수 없다 */
	BLOCKED,
	/** 여유가 0.2m 미만 */
	CAUTION,
	OK,
	/** 차 높이나 높이 제한을 모른다 */
	UNKNOWN
}
