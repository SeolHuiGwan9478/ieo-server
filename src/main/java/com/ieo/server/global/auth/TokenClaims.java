package com.ieo.server.global.auth;

import java.util.UUID;

/** 토큰에서 읽은 값. 차량에 준 토큰만 carSessionId가 있다 */
public record TokenClaims(Long userId, UUID carSessionId) {
}
