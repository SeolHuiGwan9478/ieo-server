package com.ieo.server.domain.user.dto;

/** 로컬 시연 전용 응답. 운영 환경에서는 이 응답을 반환하지 않는다. */
public record SendCodeResponse(String demoCode, long expiresInSeconds) {
}
