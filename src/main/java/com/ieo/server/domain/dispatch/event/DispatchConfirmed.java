package com.ieo.server.domain.dispatch.event;

/** 배차 요청이 확정됐다 (DSP-022). 주선사가 골랐거나 자동 확정 */
public record DispatchConfirmed(Long requestId) {
}
