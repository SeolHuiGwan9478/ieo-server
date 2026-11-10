package com.ieo.server.domain.driver.dto;

/**
 * 차주 이력 (DSP-031): 서류 인증 여부, Ieo에서 끝낸 운행 수, 상차 2시간 안에 차주가 취소한 수.
 * 차주 본인은 내 정보에서, 주선사는 요청한 차주 목록과 단골 목록에서 같은 값을 본다
 */
public record DriverRecordResponse(boolean verified, long completedTripCount, long lateCancelCount) {
}
