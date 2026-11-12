package com.ieo.server.global.config;

import com.ieo.server.domain.user.controller.CarLoginController;
import com.ieo.server.global.auth.AdminInterceptor;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	/** 수하인 페이지 주소의 앞부분. 뒤에 인수증 토큰이 붙는다 */

	/** 주선사가 보는 대기 증빙 페이지 주소의 앞부분. 뒤에 증빙 토큰이 붙는다 */
	public static final String WAIT_PROOF_PAGE = "/w";

	private final AuthInterceptor authInterceptor;
	private final AdminInterceptor adminInterceptor;
	private final CorsProperties corsProperties;

	/**
	 * /api/auth/** (로그인 전에 부르는 API)만 빼고 모두 로그인이 필요하다.
	 * /api/admin/** 은 그 위에 운영자(ADMIN) 역할까지 확인한다. 주선사·차주 계정은 403을 받는다
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor)
				.addPathPatterns("/api/**")
				.excludePathPatterns("/api/auth/**", "/api/public/**");
		registry.addInterceptor(adminInterceptor)
				.addPathPatterns("/api/admin/**");
	}

	/**
	 * 로그인 없이 여는 페이지. 데이터는 페이지가 /api/public에서 받는다.
	 * - 대기 증빙 (WAIT-020): 차주가 주선사에게 보내는 링크
	 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController(WAIT_PROOF_PAGE + "/*").setViewName("forward:/wait-proof.html");
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
