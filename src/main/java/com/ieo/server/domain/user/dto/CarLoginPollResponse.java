package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.CarLoginStatus;

/**
 * 차량이 승인 여부를 물었을 때의 답. 승인된 세션을 처음 물었을 때만 status=APPROVED와 tokens가 온다.
 * 그 뒤에는 CONSUMED로 바뀌고 tokens는 다시 오지 않는다.
 */
public record CarLoginPollResponse(CarLoginStatus status, TokenResponse tokens) {

	public static CarLoginPollResponse of(CarLoginStatus status) {
		return new CarLoginPollResponse(status, null);
	}
}
