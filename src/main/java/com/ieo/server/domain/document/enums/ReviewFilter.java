package com.ieo.server.domain.document.enums;

/** 운영자 심사 목록의 탭 (07 인계서 6.2) */
public enum ReviewFilter {
	/** 심사 대기 서류가 하나라도 있는 차주. 제출이 오래된 순 */
	PENDING,
	/** 필수 서류가 모두 승인된(인증된) 차주 */
	APPROVED,
	/** 반려된 서류를 아직 다시 내지 않은 차주 */
	REJECTED,
	/** 서류를 낸 적 있는 모든 차주 */
	ALL
}
