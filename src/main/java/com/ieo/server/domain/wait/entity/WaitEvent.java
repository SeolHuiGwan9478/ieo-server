package com.ieo.server.domain.wait.entity;

import com.ieo.server.domain.wait.enums.WaitEventSource;
import com.ieo.server.domain.wait.enums.WaitEventType;
import java.time.Instant;

/**
 * 대기 기록에 남기는 한 순간 (WAIT-025). 기사님이 확인하거나 누른 순간만 남고, 누르지 않은 순간은 없다.
 * 이동 경로는 남기지 않고 그 순간의 위치 한 점만 남긴다.
 *
 * @param at         그 순간 (차량 앱이 판단한 시각)
 * @param latitude   그 순간 차 위치. 모르면 null
 * @param longitude  그 순간 차 위치. 모르면 null
 * @param receivedAt 서버가 받은 시각. 통신이 끊겨 나중에 보낸 기록인지 알 수 있다
 */
public record WaitEvent(
		WaitEventType type,
		Instant at,
		Double latitude,
		Double longitude,
		WaitEventSource source,
		Instant receivedAt) {
}
