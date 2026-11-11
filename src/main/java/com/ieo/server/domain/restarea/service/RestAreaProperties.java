package com.ieo.server.domain.restarea.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 휴게소 데이터 (application.yml의 ieo.rest-area). 키가 비어 있으면 그 API는 부르지 않는다.
 *
 * @param expresswayKey   한국도로공사 고속도로 공공데이터 포털(data.ex.co.kr) 인증키. 환경변수 EX_API_KEY
 * @param publicDataKey   공공데이터포털(data.go.kr) 일반 인증키(Decoding). 환경변수 DATA_GO_KR_KEY
 * @param expresswayUrl   도로공사 노선별 휴게시설 현황 (apiId 0317)
 * @param publicDataUrl   전국휴게소정보표준데이터
 * @param autoSync        일주일마다 갱신할지. 테스트는 끈다
 * @param minKeepRatio    이번에 받은 수가 지금 저장된 수의 이 비율보다 적으면 갱신을 멈춘다 (API가 일부만 준 경우)
 */
@ConfigurationProperties("ieo.rest-area")
public record RestAreaProperties(
		String expresswayKey,
		String publicDataKey,
		@DefaultValue("https://data.ex.co.kr/openapi/business/serviceAreaRoute") String expresswayUrl,
		@DefaultValue("https://api.data.go.kr/openapi/tn_pubr_public_rest_area_api") String publicDataUrl,
		@DefaultValue("true") boolean autoSync,
		@DefaultValue("0.9") double minKeepRatio) {

	public boolean hasExpresswayKey() {
		return expresswayKey != null && !expresswayKey.isBlank();
	}

	public boolean hasPublicDataKey() {
		return publicDataKey != null && !publicDataKey.isBlank();
	}
}
