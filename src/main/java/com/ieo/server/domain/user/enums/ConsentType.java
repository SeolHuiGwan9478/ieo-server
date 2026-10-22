package com.ieo.server.domain.user.enums;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 동의 항목. requiredFor에 들어 있는 역할은 가입할 때 반드시 동의해야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ConsentType {
	TERMS("이용약관", Set.of(UserRole.DRIVER, UserRole.BROKER)),
	PRIVACY("개인정보 수집·이용", Set.of(UserRole.DRIVER, UserRole.BROKER)),
	LOCATION("위치 정보 수집", Set.of(UserRole.DRIVER)),
	BROKER_SHARE("주선사에 위치·운행 정보 공유", Set.of()),
	VOICE("음성 데이터 처리", Set.of()),
	MARKETING("마케팅 수신", Set.of());

	private final String description;
	private final Set<UserRole> requiredFor;

	public boolean isRequiredFor(UserRole role) {
		return requiredFor.contains(role);
	}
}
