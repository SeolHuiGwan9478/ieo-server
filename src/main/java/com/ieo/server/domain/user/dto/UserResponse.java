package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;

public record UserResponse(Long id, String phone, String name, UserRole role, UserStatus status) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getPhone(), user.getName(), user.getRole(), user.getStatus());
	}
}
