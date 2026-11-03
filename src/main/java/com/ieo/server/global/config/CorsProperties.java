package com.ieo.server.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 브라우저에서 API를 부를 수 있는 주소 (주선사 웹 콘솔 등). 비어 있으면 아무 주소도 허용하지 않는다 */
@ConfigurationProperties("ieo.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null
				? List.of()
				: allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
	}
}
