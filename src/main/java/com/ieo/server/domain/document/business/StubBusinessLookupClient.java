package com.ieo.server.domain.document.business;

import com.ieo.server.domain.document.enums.BusinessStatus;
import java.time.LocalDate;

/**
 * 시연·테스트용 가짜 국세청 (ieo.nts.stub=true, 키가 없을 때만). 사업자번호 아홉째 자리로 결과를 정한다.
 * 0~6 계속사업자(일치), 7 휴업자, 8 폐업자, 9 불일치. 결과에 stub=true가 붙어 화면에 "시연용"으로 보인다.
 */
public class StubBusinessLookupClient implements BusinessLookupClient {

	@Override
	public BusinessLookup lookup(String businessNumber, LocalDate openedOn, String ownerName) {
		char selector = businessNumber.charAt(8);
		return switch (selector) {
			case '7' -> BusinessLookup.match(BusinessStatus.SUSPENDED, "부가가치세 일반과세자", null, true);
			case '8' -> BusinessLookup.match(BusinessStatus.CLOSED, "부가가치세 간이과세자", openedOn.plusYears(3), true);
			case '9' -> BusinessLookup.mismatch("확인할 수 없습니다.", true);
			default -> BusinessLookup.match(BusinessStatus.ACTIVE, "부가가치세 일반과세자", null, true);
		};
	}
}
