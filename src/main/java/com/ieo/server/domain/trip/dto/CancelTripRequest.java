package com.ieo.server.domain.trip.dto;

import com.ieo.server.domain.dispatch.enums.CancelReason;
import jakarta.validation.constraints.Size;

/** 운행 취소. Ieo 배차로 생긴 운행은 사유가 필요하다(DSP-030). 직접 등록한 운행은 비워도 된다 */
public record CancelTripRequest(CancelReason reason, @Size(max = 200) String memo) {
}
