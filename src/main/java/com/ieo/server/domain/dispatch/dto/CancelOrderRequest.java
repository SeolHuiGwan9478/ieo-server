package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.enums.CancelReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 주선사의 배차 취소. reopen을 비우거나 true면 오더를 다시 모집하고, false면 내린다 */
public record CancelOrderRequest(@NotNull CancelReason reason, @Size(max = 200) String memo, Boolean reopen) {
}
