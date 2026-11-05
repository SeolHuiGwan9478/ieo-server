package com.ieo.server.domain.dispatch.event;

/** 기다리던 배차 요청이 마감됐다: 다른 차주가 확정됐거나 주선사가 오더를 내렸다 */
public record DispatchDeclined(Long requestId) {
}
