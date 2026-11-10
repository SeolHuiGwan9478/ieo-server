package com.ieo.server.domain.document.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 국세청 사업자등록정보 진위확인 API (application.yml의 ieo.nts).
 * 공공데이터포털(data.go.kr) "국세청_사업자등록정보 진위확인 및 상태조회 서비스"(15081808)를 활용신청하면 바로 승인되고 키가 나온다.
 *
 * @param serviceKey 포털의 "일반 인증키 (Decoding)". 환경변수 NTS_SERVICE_KEY. 비어 있으면 조회하지 않는다
 * @param baseUrl    api.odcloud.kr
 * @param stub       시연용. 키 없이 사업자번호 아홉째 자리로 정해진 가짜 결과를 돌려준다 (StubBusinessLookupClient)
 */
@ConfigurationProperties("ieo.nts")
public record NtsProperties(
		String serviceKey,
		@DefaultValue("https://api.odcloud.kr") String baseUrl,
		@DefaultValue("false") boolean stub) {

	public boolean hasKey() {
		return serviceKey != null && !serviceKey.isBlank();
	}
}
