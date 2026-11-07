package com.ieo.server.domain.site.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ieo.kakao")
public record KakaoLocalProperties(
        String restApiKey,
        @DefaultValue("https://dapi.kakao.com") String baseUrl) {
}