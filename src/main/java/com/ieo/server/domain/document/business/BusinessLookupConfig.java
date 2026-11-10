package com.ieo.server.domain.document.business;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 키가 있으면 국세청, 없는데 stub이면 시연용 스텁, 둘 다 아니면 "조회 안 함" */
@Slf4j
@Configuration
public class BusinessLookupConfig {

	@Bean
	public BusinessLookupClient businessLookupClient(NtsProperties properties) {
		if (properties.hasKey()) {
			if (properties.stub()) {
				log.warn("ieo.nts.stub=true 이지만 키가 있어 국세청 API를 씁니다");
			}
			return new NtsBusinessLookupClient(properties);
		}
		if (properties.stub()) {
			log.warn("국세청 진위확인을 시연용 스텁으로 대신합니다 (ieo.nts.stub=true). 운영에서는 NTS_SERVICE_KEY를 넣습니다");
			return new StubBusinessLookupClient();
		}
		log.info("국세청 API 키가 없어 사업자등록증 진위확인을 건너뜁니다 (NTS_SERVICE_KEY)");
		return (businessNumber, openedOn, ownerName) -> BusinessLookup.skipped("국세청 API 키가 없어 조회하지 않았어요");
	}
}
