package com.ieo.server.domain.document.enums;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 차주가 내는 서류 (docs/design/07_운영자_서류심사_인계.md 4장).
 * 필수 서류의 최신 제출이 모두 승인되면 차주는 "인증됨"이다. checks는 운영자가 사진을 보고 확인할 항목이다.
 * 사업자등록증은 입력한 번호·개업일·대표자를 국세청 진위확인 API로 맞춰 본 결과가 같이 보인다 (반자동, BusinessCheck).
 */
@Getter
@RequiredArgsConstructor
public enum DocumentType implements DocumentKind {
	BUSINESS_REGISTRATION("사업자등록증", true, List.of(
			"글씨가 읽히는가",
			"상호·사업자번호·대표자가 보이는가",
			"입력한 사업자번호·개업일·대표자가 사진과 같은가 (국세청 조회는 입력값 기준이다)",
			"대표자 이름이 가입자 이름과 같은가 (다르면 위·수탁 계약서를 함께 받는다)")),
	VEHICLE_REGISTRATION("자동차등록증", true, List.of(
			"차량 번호가 앱에 등록한 번호와 같은가",
			"소유자가 본인인가 (다르면 위·수탁 계약서를 함께 받는다)",
			"사업용 차량인가")),
	DRIVER_LICENSE("운전면허증", true, List.of(
			"이름이 가입자 이름과 같은가",
			"갱신 기간이 지나지 않았는가",
			"차종·톤수에 맞는 면허 종류인가")),
	FREIGHT_QUALIFICATION("화물운송종사 자격증", true, List.of(
			"이름이 가입자 이름과 같은가",
			"자격 번호가 보이는가")),
	CONSIGNMENT_CONTRACT("위·수탁(지입) 계약서", false, List.of(
			"사업자·차량 명의가 본인과 다를 때 받는다",
			"당사자와 차량 번호가 일치하는가")),
	CARGO_INSURANCE("적재물배상책임보험증서", false, List.of(
			"보험 기간이 유효한가",
			"차량 번호가 같은가"));

	private final String label;
	/** 필수 서류인지. 선택·조건부 서류는 인증 여부에 들어가지 않는다 */
	private final boolean required;
	/** 운영자가 사진을 보고 확인할 항목 */
	private final List<String> checks;

	public static List<DocumentType> requiredTypes() {
		return Arrays.stream(values()).filter(DocumentType::isRequired).toList();
	}
}
