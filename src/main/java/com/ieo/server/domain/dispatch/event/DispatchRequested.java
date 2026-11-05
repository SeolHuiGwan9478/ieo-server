package com.ieo.server.domain.dispatch.event;

import java.time.Instant;

/** 차주가 배차 요청을 보냈고 주선사 확정을 기다린다. 알림은 만료 시각에 만료를 알린다 (DSP-023) */
public record DispatchRequested(Long requestId, Instant expiresAt) {
}
