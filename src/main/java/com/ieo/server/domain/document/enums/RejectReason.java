package com.ieo.server.domain.document.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 반려 사유 (07 인계서 6.4). message는 차주에게 그대로 보인다. OTHER는 운영자가 직접 적는다.
 * 값을 더하면 기존 DB의 CHECK 제약도 고친다 (scripts/driver-documents-business-check.sql)
 */
@Getter
@RequiredArgsConstructor
public enum RejectReason {
	UNREADABLE("글씨 안 보임", "글씨가 잘 안 보여요. 밝은 곳에서 다시 찍어 주세요"),
	CROPPED("잘림", "서류 전체가 나오게 찍어 주세요"),
	WRONG_DOCUMENT("다른 서류", "다른 서류가 올라왔어요. 안내된 서류를 찍어 주세요"),
	NAME_MISMATCH("이름 불일치", "가입한 이름과 서류의 이름이 달라요"),
	BUSINESS_MISMATCH("국세청 정보 불일치", "입력한 사업자등록번호·개업일·대표자가 국세청 등록 정보와 달라요. 사업자등록증을 보고 다시 입력해 주세요"),
	BUSINESS_CLOSED("휴업·폐업 사업자", "휴업하거나 폐업한 사업자예요. 영업 중인 사업자등록증을 올려 주세요"),
	VEHICLE_MISMATCH("차량 번호 불일치", "등록한 차량 번호와 서류의 번호가 달라요"),
	EXPIRED("유효 기간 지남", "유효 기간이 지났어요. 최신 서류를 올려 주세요"),
	CONTRACT_REQUIRED("지입 계약서 필요", "명의가 달라요. 위·수탁 계약서를 함께 올려 주세요"),
	OTHER("기타", null);

	private final String label;
	/** 차주에게 보이는 문구. OTHER는 운영자가 적은 글이 대신 보인다 */
	private final String message;

	public boolean requiresNote() {
		return this == OTHER;
	}
}
