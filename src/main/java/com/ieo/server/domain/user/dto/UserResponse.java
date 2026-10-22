package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserRole;

public record UserResponse(
		Long id,
		String phone,
		String name,
		UserRole role,
		boolean consentLocation,
		boolean consentBrokerShare,
		boolean consentVoice) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getPhone(),
				user.getName(),
				user.getRole(),
				user.isConsentLocation(),
				user.isConsentBrokerShare(),
				user.isConsentVoice());
	}
}
