package com.ieo.server.domain.dispatch.event;

/** 주선사가 확정된 배차를 취소했다. 기사님에게 알린다 (DSP-030, 09 알림) */
public record DispatchCanceled(Long requestId) {
}
