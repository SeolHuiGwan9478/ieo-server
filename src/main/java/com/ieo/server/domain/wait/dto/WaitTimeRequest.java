package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.wait.enums.WaitEventSource;
import java.time.Instant;

/**
 * 차량 앱이 알리는 순간 (대기 시작, "상차 시작", 떠남). 모두 비울 수 있다.
 *
 * @param at        앱이 판단한 시각. 비우면 서버가 받은 시각. 통신이 끊겼다가 나중에 보내도 시각이 정확하게 남는다
 * @param latitude  그 순간 차 위치 (WAIT-025). 증빙에서 상하차지와의 거리로 보여 준다
 * @param longitude 그 순간 차 위치
 * @param source    기사님이 어떻게 알렸나. 비우면 버튼
 */
public record WaitTimeRequest(Instant at, Double latitude, Double longitude, WaitEventSource source) {

	public static WaitTimeRequest at(Instant at) {
		return new WaitTimeRequest(at, null, null, null);
	}
}
