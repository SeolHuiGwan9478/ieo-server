package com.ieo.server.domain.user.dto;

import jakarta.validation.constraints.Size;

/** 탈퇴 사유는 선택. 서비스 개선용으로만 남긴다. */
public record WithdrawRequest(@Size(max = 200) String reason) {
}
