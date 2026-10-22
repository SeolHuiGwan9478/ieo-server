package com.ieo.server.domain.user.enums;

/**
 * 계정 상태. ACTIVE와 SUSPENDED는 서로 오갈 수 있고, WITHDRAWN은 되돌릴 수 없다.
 */
public enum UserStatus {
	/** 정상 */
	ACTIVE,
	/** 이용 제한 (허위 오더, 불법 주선 등. 플랫폼법 DSP-043) */
	SUSPENDED,
	/** 탈퇴. 휴대폰 번호와 이름은 지워진다 */
	WITHDRAWN
}
