package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import java.time.Instant;

/** 모달에 보여 줄 기존 계정 정보. 문자 인증을 통과한 사람에게만 내려간다. */
public record ExistingAccountResponse(String name, UserRole role, UserStatus status, Instant joinedAt, Instant lastLoginAt) {

	public static ExistingAccountResponse from(User user) {
		return new ExistingAccountResponse(user.getName(), user.getRole(), user.getStatus(), user.getCreatedAt(), user.getLastLoginAt());
	}
}
