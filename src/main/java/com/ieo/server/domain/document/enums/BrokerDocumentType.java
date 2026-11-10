package com.ieo.server.domain.document.enums;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주선사 회사가 내는 서류 (07 인계서 13장). 둘 다 필수이고 둘 다 승인되면 회사가 "인증됨"이다.
 * 사업자등록번호·허가 번호·상호·대표자는 회사 등록 때 글자로 받아 두었으므로, 운영자는 사진이 그 값과 같은지 본다.
 * 사업자등록증은 등록된 사업자번호로 국세청 진위확인을 돌린다 (BusinessCheck).
 */
@Getter
@RequiredArgsConstructor
public enum BrokerDocumentType implements DocumentKind {
	BUSINESS_REGISTRATION("사업자등록증", true, List.of(
			"글씨가 읽히는가",
			"사업자번호·상호·대표자가 등록한 회사 정보와 같은가 (국세청 조회는 등록된 번호 기준이다)",
			"업태·종목에 화물 운송주선이 있는가")),
	BROKER_LICENSE("운송주선사업 허가증", true, List.of(
			"허가 번호가 등록한 번호와 같은가",
			"상호·대표자가 사업자등록증과 같은가",
			"허가 취소·정지 표시가 없는가"));

	private final String label;
	private final boolean required;
	private final List<String> checks;

	public static List<BrokerDocumentType> requiredTypes() {
		return Arrays.stream(values()).filter(BrokerDocumentType::isRequired).toList();
	}
}
