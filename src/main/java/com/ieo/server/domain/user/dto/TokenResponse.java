package com.ieo.server.domain.user.dto;

public record TokenResponse(String accessToken, String refreshToken, boolean newUser) {
}
