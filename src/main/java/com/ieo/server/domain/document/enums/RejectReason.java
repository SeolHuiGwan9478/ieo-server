package com.ieo.server.domain.document.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * 반려 사유 (07 인계서 6.4). message는 낸 사람(차주·주선사 담당자)에게 그대로 보인다. OTHER는 운영자가 직접 적는다.
 * subjects로 차주 서류용과 주선사 서류용을 가른다 (차량 번호 불일치는 차주만, 허가 번호 불일치는 주선사만).
 * 값을 더하면 기존 DB의 CHECK 제약도 고친다 (scripts/driver-documents-business-check.sql)
 */
@Getter
public enum RejectReason {
	UNREADABLE("글씨 안 보임", "글씨가 잘 안 보여요. 밝은 곳에서 다시 찍어 주세요", ReviewSubject.DRIVER, ReviewSubject.BROKER),
	CROPPED("잘림", "서류 전체가 나오게 찍어 주세요", ReviewSubject.DRIVER, ReviewSubject.BROKER),
	WRONG_DOCUMENT("다른 서류", "다른 서류가 올라왔어요. 안내된 서류를 찍어 주세요", ReviewSubject.DRIVER, ReviewSubject.BROKER),
	NAME_MISMATCH("이름 불일치", "가입한 이름과 서류의 이름이 달라요", ReviewSubject.DRIVER),
	COMPANY_MISMATCH("상호·대표자 불일치", "등록한 상호·대표자와 서류의 내용이 달라요. 설정의 회사 정보를 확인해 주세요", ReviewSubject.BROKER),
	BUSINESS_MISMATCH("국세청 정보 불일치", "입력한 사업자등록번호·개업일·대표자가 국세청 등록 정보와 달라요. 사업자등록증을 보고 다시 입력해 주세요",
			ReviewSubject.DRIVER, ReviewSubject.BROKER),
	BUSINESS_CLOSED("휴업·폐업 사업자", "휴업하거나 폐업한 사업자예요. 영업 중인 사업자등록증을 올려 주세요", ReviewSubject.DRIVER, ReviewSubject.BROKER),
	VEHICLE_MISMATCH("차량 번호 불일치", "등록한 차량 번호와 서류의 번호가 달라요", ReviewSubject.DRIVER),
	LICENSE_MISMATCH("허가 번호 불일치", "등록한 운송주선사업 허가 번호와 서류의 번호가 달라요. 설정의 회사 정보를 확인해 주세요", ReviewSubject.BROKER),
	EXPIRED("유효 기간 지남", "유효 기간이 지났어요. 최신 서류를 올려 주세요", ReviewSubject.DRIVER, ReviewSubject.BROKER),
	CONTRACT_REQUIRED("지입 계약서 필요", "명의가 달라요. 위·수탁 계약서를 함께 올려 주세요", ReviewSubject.DRIVER),
	/** 차주가 차량 번호·톤수·차종을 바꾸면 서버가 자동차등록증을 이 사유로 반려한다 (VEH-001) */
	VEHICLE_CHANGED("차량 정보 변경", "차량 정보를 바꿔서 자동차등록증을 다시 확인해야 해요. 지금 차의 등록증을 올려 주세요", ReviewSubject.DRIVER),
	OTHER("기타", null, ReviewSubject.DRIVER, ReviewSubject.BROKER);

	private final String label;
	/** 낸 사람에게 보이는 문구. OTHER는 운영자가 적은 글이 대신 보인다 */
	private final String message;
	private final Set<ReviewSubject> subjects;

	RejectReason(String label, String message, ReviewSubject... subjects) {
		this.label = label;
		this.message = message;
		this.subjects = EnumSet.copyOf(Arrays.asList(subjects));
	}

	public boolean requiresNote() {
		return this == OTHER;
	}

	public boolean appliesTo(ReviewSubject subject) {
		return subjects.contains(subject);
	}

	/** 반려 창에 보여 줄 사유. 선언 순서대로 */
	public static List<RejectReason> forSubject(ReviewSubject subject) {
		return Arrays.stream(values()).filter(reason -> reason.appliesTo(subject)).toList();
	}
}
