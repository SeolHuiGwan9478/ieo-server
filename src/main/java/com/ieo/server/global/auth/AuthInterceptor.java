package com.ieo.server.global.auth;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authorization: Bearer {accessToken}을 확인해서 요청 attribute "userId"에 넣는다.
 * 컨트롤러는 @RequestAttribute("userId")로 꺼내 쓴다. 적용 범위는 WebConfig에서 정한다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

	public static final String USER_ID = "userId";
	private static final String BEARER = "Bearer ";

	private final JwtProvider jwtProvider;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		request.setAttribute(USER_ID, jwtProvider.parseAccessToken(header.substring(BEARER.length())));
		return true;
	}
}
