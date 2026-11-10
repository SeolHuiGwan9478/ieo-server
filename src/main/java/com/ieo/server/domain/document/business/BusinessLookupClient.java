package com.ieo.server.domain.document.business;

import java.time.LocalDate;

/**
 * 사업자등록번호·개업일·대표자가 국세청 등록 정보와 맞는지 본다.
 * 구현은 셋이다 (BusinessLookupConfig): 국세청 API, 시연용 스텁, 키가 없을 때의 "조회 안 함".
 * 예외를 던지지 않고 ERROR 결과로 돌려준다. 서류 제출이 외부 장애로 막히면 안 되기 때문이다.
 */
public interface BusinessLookupClient {

	/**
	 * @param businessNumber 숫자 10자리
	 * @param openedOn       개업일
	 * @param ownerName      대표자 이름
	 */
	BusinessLookup lookup(String businessNumber, LocalDate openedOn, String ownerName);
}
