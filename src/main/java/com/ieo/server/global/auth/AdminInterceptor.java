package com.ieo.server.global.auth;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/admin/** 은 운영자(ADMIN) 계정만 쓴다. AuthInterceptor가 먼저 토큰을 확인해 userId를 넣어 둔 뒤에 돈다.
 * 차주의 면허증·사업자등록증을 다루므로 주선사·차주 계정은 403(ADMIN_403_ROLE)을 받는다 (07 인계서 7장).
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

	private final UserRepository userRepository;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return true;
		}
		if (!(request.getAttribute(AuthInterceptor.USER_ID) instanceof Long userId) || !isAdmin(userId)) {
			throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
		}
		return true;
	}

	private boolean isAdmin(Long userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.map(user -> user.getRole() == UserRole.ADMIN)
				.orElse(false);
	}
}
