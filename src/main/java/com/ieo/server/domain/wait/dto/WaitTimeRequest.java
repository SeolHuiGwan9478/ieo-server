package com.ieo.server.domain.wait.dto;

import java.time.Instant;

/**
 * 차량 앱이 판단한 시각 (3분 정차 확인, "상차 시작", 이탈). 비우면 서버가 받은 시각.
 * 터널 등에서 통신이 끊겼다가 나중에 보내도 시각이 정확하게 남게 한다
 */
public record WaitTimeRequest(Instant at) {
}
