package com.ieo.server.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 휴대폰 앱의 FCM 토큰 */
public record PushTokenRequest(@NotBlank @Size(max = 512) String token) {
}
