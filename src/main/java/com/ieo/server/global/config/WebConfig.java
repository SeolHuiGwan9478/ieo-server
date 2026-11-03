package com.ieo.server.global.config;

import com.ieo.server.domain.user.controller.CarLoginController;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final AuthInterceptor authInterceptor;
	private final CorsProperties corsProperties;

	/** /api/auth/** (로그인 전에 부르는 API)만 빼고 모두 로그인이 필요하다. */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor)
				.addPathPatterns("/api/**")
				.excludePathPatterns("/api/auth/**", "/api/public/**");
	}

	/** 토큰은 쿠키가 아니라 Authorization 헤더로 오가므로 자격 증명(쿠키)은 허용하지 않는다. */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (corsProperties.allowedOrigins().isEmpty()) {
			return;
		}
		registry.addMapping("/api/**")
				.allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CarLoginController.POLL_SECRET_HEADER)
				.maxAge(3600);
	}
}
