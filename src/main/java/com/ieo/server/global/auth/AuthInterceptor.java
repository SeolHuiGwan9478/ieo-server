package com.ieo.server.global.auth;

import com.ieo.server.domain.user.service.CarSessionService;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authorization: Bearer {accessToken}을 확인해서 요청 attribute "userId"에 넣는다.
 * 컨트롤러는 @RequestAttribute("userId")로 꺼내 쓴다. 적용 범위는 WebConfig에서 정한다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

	public static final String USER_ID = "userId";
	public static final String CAR_SESSION_ID = "carSessionId";
	private static final String BEARER = "Bearer ";

	private final JwtProvider jwtProvider;
	private final CarSessionService carSessionService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// 브라우저의 CORS 사전 요청(OPTIONS)에는 토큰이 실리지 않는다
		if (CorsUtils.isPreFlightRequest(request)) {
			return true;
		}
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		TokenClaims claims = jwtProvider.parseAccessToken(header.substring(BEARER.length()));
		// 휴대폰에서 끊은 차량은 액세스 토큰이 남아 있어도 바로 막는다
		if (claims.carSessionId() != null && !carSessionService.isActive(claims.carSessionId())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		request.setAttribute(USER_ID, claims.userId());
		if (claims.carSessionId() != null) {
			request.setAttribute(CAR_SESSION_ID, claims.carSessionId());
		}
		return true;
	}
}
