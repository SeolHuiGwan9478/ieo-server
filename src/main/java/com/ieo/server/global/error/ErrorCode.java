package com.ieo.server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않아요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "로그인이 필요해요."),
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "COMMON_501", "아직 구현되지 않은 기능이에요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했어요."),

	// 사용자
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없어요."),
	CAR_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CAR_SESSION_404", "로그인된 차량을 찾을 수 없어요."),
	CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "USER_400_CONSENT", "필수 동의는 철회할 수 없습니다. 동의하지 않으려면 탈퇴해 주세요."),
	USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "USER_403", "이용이 제한됐거나 탈퇴한 계정이에요."),
	USER_INVALID_STATUS(HttpStatus.CONFLICT, "USER_409", "지금 계정 상태에서는 할 수 없는 요청이에요."),

	// 인증
	AUTH_CODE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_RESEND", "1분 뒤에 다시 받아 주세요."),
	AUTH_CODE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_DAILY", "오늘은 더 받을 수 없어요."),
	AUTH_CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_LOCKED", "10분 뒤에 다시 해 주세요."),
	AUTH_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_400_CODE", "인증번호가 달라요."),
	AUTH_CODE_EXPIRED(HttpStatus.GONE, "AUTH_410_CODE", "인증 시간이 지났어요."),
	AUTH_PHONE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_PHONE", "휴대폰 인증이 만료되었습니다. 다시 인증해 주세요."),
	AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요."),
	AUTH_PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_409_PHONE", "이미 가입된 번호예요."),
	AUTH_REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "AUTH_400_CONSENT", "필수 약관에 동의해 주세요."),
	AUTH_SOCIAL_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_503_SOCIAL", "아직 지원하지 않는 로그인 방법입니다. 휴대폰 번호로 로그인해 주세요."),
	AUTH_SOCIAL_PROVIDER_REJECTED(HttpStatus.UNAUTHORIZED, "AUTH_401_SOCIAL_PROVIDER", "소셜 계정 확인에 실패했습니다. 다시 시도해 주세요."),
	AUTH_SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_SOCIAL", "소셜 인증이 만료되었습니다. 다시 로그인해 주세요."),
	AUTH_SOCIAL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_409_SOCIAL", "이미 가입된 소셜 계정이에요."),

	// 차량 QR 로그인
	CAR_LOGIN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAR_LOGIN_404", "로그인 요청을 찾을 수 없습니다. 차량 화면의 코드를 확인해 주세요."),
	CAR_LOGIN_EXPIRED(HttpStatus.GONE, "CAR_LOGIN_410", "로그인 요청이 만료되었습니다. 차량 화면의 새 QR을 찍어 주세요."),
	CAR_LOGIN_ALREADY_DECIDED(HttpStatus.CONFLICT, "CAR_LOGIN_409", "이미 승인하거나 거절한 요청이에요."),
	CAR_LOGIN_NOT_APPROVED(HttpStatus.CONFLICT, "CAR_LOGIN_409_NOT_APPROVED", "아직 승인되지 않은 요청이에요."),

	// 차량
	VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "VEHICLE_404", "차량을 찾을 수 없어요."),
	VEHICLE_INVALID_PLATE(HttpStatus.BAD_REQUEST, "VEHICLE_400_PLATE", "차량 번호를 확인해 주세요. 예: 12가3456, 경기82사1234"),
	VEHICLE_INVALID_DIMENSION(HttpStatus.BAD_REQUEST, "VEHICLE_400_DIMENSION", "트럭 폭·길이·중량·축 수가 입력할 수 있는 범위를 벗어났어요."),
	VEHICLE_INVALID_FUEL_ECONOMY(HttpStatus.BAD_REQUEST, "VEHICLE_400_FUEL", "연비는 1.5에서 15km/L 사이로 입력해 주세요."),
	VEHICLE_INVALID_HEIGHT(HttpStatus.BAD_REQUEST, "VEHICLE_400_HEIGHT", "차량 높이는 1.5m에서 4.2m 사이로 입력해 주세요."),
	VEHICLE_DUPLICATE_PLATE(HttpStatus.CONFLICT, "VEHICLE_409_PLATE", "이미 다른 계정에 등록된 차량 번호입니다. 고객센터로 문의해 주세요."),
	VEHICLE_NOT_DRIVER(HttpStatus.FORBIDDEN, "VEHICLE_403_ROLE", "차주 계정만 차량을 등록할 수 있어요."),
	VEHICLE_LIMIT(HttpStatus.CONFLICT, "VEHICLE_409_LIMIT", "차량은 한 대만 등록할 수 있어요. 내 차량에서 정보를 고쳐 주세요."),
	VEHICLE_INVALID_VIN(HttpStatus.BAD_REQUEST, "VEHICLE_400_VIN", "차대번호 형식이 아니에요."),
	VEHICLE_VIN_OTHER_OWNER(HttpStatus.CONFLICT, "VEHICLE_409_VIN", "이 차량은 다른 계정에 등록돼 있습니다. 내 차라면 고객센터로 문의해 주세요."),

	// 주선사
	BROKER_NOT_FOUND(HttpStatus.NOT_FOUND, "BROKER_404", "주선사를 찾을 수 없어요."),
	BROKER_NO_MEMBERSHIP(HttpStatus.NOT_FOUND, "BROKER_404_MEMBER", "소속된 주선사가 없습니다. 회사를 등록하거나 대표에게 초대를 요청해 주세요."),
	BROKER_NOT_BROKER(HttpStatus.FORBIDDEN, "BROKER_403_ROLE", "주선사 계정만 쓸 수 있어요."),
	BROKER_INVALID_BUSINESS_NUMBER(HttpStatus.BAD_REQUEST, "BROKER_400_BUSINESS_NUMBER", "사업자등록번호가 올바르지 않습니다. 숫자 10자리를 확인해 주세요."),
	BROKER_INVALID_SETTLEMENT_DAYS(HttpStatus.BAD_REQUEST, "BROKER_400_SETTLEMENT_DAYS", "정산 주기는 0일에서 365일 사이로 입력해 주세요."),
	BROKER_INVALID_FEE_RATE(HttpStatus.BAD_REQUEST, "BROKER_400_FEE_RATE", "수수료율은 0%에서 99.9% 사이로 입력해 주세요."),
	BROKER_INVALID_LICENSE_NUMBER(HttpStatus.BAD_REQUEST, "BROKER_400_LICENSE", "운송주선사업 허가 번호를 입력해 주세요."),
	BROKER_DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "BROKER_409_BUSINESS_NUMBER", "이미 등록된 회사입니다. 회사 대표에게 초대를 요청해 주세요."),
	BROKER_ALREADY_MEMBER(HttpStatus.CONFLICT, "BROKER_409_MEMBER", "이미 소속된 주선사가 있어요."),
	BROKER_MEMBER_REMOVED(HttpStatus.FORBIDDEN, "BROKER_403_REMOVED", "회사에서 나온 계정입니다. 다른 회사에 합류하려면 탈퇴 후 다시 가입해 주세요."),
	BROKER_OWNER_ONLY(HttpStatus.FORBIDDEN, "BROKER_403_OWNER", "대표만 할 수 있어요."),
	BROKER_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "BROKER_MEMBER_404", "담당자를 찾을 수 없어요."),
	BROKER_LAST_OWNER(HttpStatus.CONFLICT, "BROKER_409_LAST_OWNER", "대표가 한 명뿐입니다. 다른 담당자를 대표로 지정한 뒤 다시 시도해 주세요."),
	BROKER_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "BROKER_INVITATION_404", "초대를 찾을 수 없어요."),
	BROKER_INVITATION_DUPLICATE(HttpStatus.CONFLICT, "BROKER_INVITATION_409_DUPLICATE", "이미 초대한 번호입니다. 초대를 취소한 뒤 다시 보내 주세요."),
	BROKER_INVITATION_ALREADY_MEMBER(HttpStatus.CONFLICT, "BROKER_INVITATION_409_MEMBER", "이미 우리 회사 담당자인 번호예요."),
	BROKER_INVITATION_CLOSED(HttpStatus.CONFLICT, "BROKER_INVITATION_409_CLOSED", "만료됐거나 이미 처리된 초대예요."),
	BROKER_INVITATION_OTHER_COMPANY(HttpStatus.CONFLICT, "BROKER_INVITATION_409_OTHER_COMPANY", "이미 다른 회사에 소속된 번호예요. 그 회사에서 나온 뒤에 초대할 수 있어요."),
	BROKER_INVITATION_NOT_BROKER(HttpStatus.BAD_REQUEST, "BROKER_INVITATION_400_ROLE", "차주로 가입된 번호예요. 담당자는 주선사 계정만 될 수 있어요."),
	PLAN_INVALID(HttpStatus.BAD_REQUEST, "PLAN_400", "플랜을 골라 주세요."),
	PLAN_UPGRADE_REQUIRED(HttpStatus.FORBIDDEN, "PLAN_403_UPGRADE", "지금 플랜에서는 쓸 수 없는 기능입니다. 플랜을 올려 주세요."),
	PLAN_MEMBER_LIMIT(HttpStatus.FORBIDDEN, "PLAN_403_MEMBER_LIMIT", "지금 플랜의 담당자 수 한도에 도달했어요."),
	DRIVER_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "DRIVER_LINK_404", "단골 연결을 찾을 수 없어요."),
	DRIVER_LINK_NOT_DRIVER(HttpStatus.FORBIDDEN, "DRIVER_LINK_403_ROLE", "차주 계정만 단골 연결을 수락할 수 있어요."),
	DRIVER_LINK_DUPLICATE(HttpStatus.CONFLICT, "DRIVER_LINK_409_DUPLICATE", "이미 연결을 요청한 번호입니다. 요청을 취소한 뒤 다시 보내 주세요."),
	DRIVER_LINK_ALREADY_LINKED(HttpStatus.CONFLICT, "DRIVER_LINK_409_LINKED", "이미 단골로 연결된 차주예요."),
	DRIVER_LINK_NOT_DRIVER_PHONE(HttpStatus.BAD_REQUEST, "DRIVER_LINK_400_ROLE", "차주 계정이 아닌 번호예요. 차주 앱에 가입한 번호로 요청해 주세요."),
	DRIVER_LINK_CLOSED(HttpStatus.CONFLICT, "DRIVER_LINK_409_CLOSED", "만료됐거나 이미 처리된 요청이에요."),

	// 배차
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404", "오더를 찾을 수 없어요."),
	ORDER_INVALID(HttpStatus.BAD_REQUEST, "ORDER_400", "오더 정보가 올바르지 않아요."),
	ORDER_TARGET_NOT_LINKED(HttpStatus.BAD_REQUEST, "ORDER_400_TARGET", "단골로 연결된 차주에게만 지정 배차할 수 있습니다. 오더 마켓에 공개로 올리거나 먼저 단골로 연결해 주세요."),
	ORDER_NOT_BROKER(HttpStatus.FORBIDDEN, "ORDER_403_ROLE", "주선사 계정만 오더를 관리할 수 있어요."),
	ORDER_CONFLICT(HttpStatus.CONFLICT, "ORDER_409_CONFLICT", "다른 담당자가 먼저 처리했습니다. 새로 고침해 주세요."),
	ORDER_NOT_ASSIGNED(HttpStatus.CONFLICT, "ORDER_409_NOT_ASSIGNED", "배차가 확정된 오더가 아니에요."),
	ORDER_ALREADY_REPORTED(HttpStatus.CONFLICT, "ORDER_409_REPORTED", "이미 신고한 오더예요."),
	ORDER_NOT_OPEN(HttpStatus.CONFLICT, "ORDER_409_CLOSED", "모집이 끝난 오더예요."),
	ORDER_LOCKED(HttpStatus.CONFLICT, "ORDER_409_LOCKED", "배차가 확정된 오더는 거래처와 청구운임만 고칠 수 있습니다. 조건을 바꾸려면 배차를 취소한 뒤 다시 모집해 주세요."),
	ORDER_HAS_REQUESTS(HttpStatus.CONFLICT, "ORDER_409_REQUESTS", "대기 중인 배차 요청이 있어 차주에게 보이는 조건은 바꿀 수 없습니다. 요청이 확정·만료된 뒤 고치거나, 지금은 거래처와 청구운임만 고쳐 주세요."),
	DISPATCH_NOT_DRIVER(HttpStatus.FORBIDDEN, "DISPATCH_403_ROLE", "차주 계정만 배차를 요청할 수 있어요."),
	DISPATCH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "DISPATCH_404", "배차 요청을 찾을 수 없어요."),
	DISPATCH_VEHICLE_MISMATCH(HttpStatus.BAD_REQUEST, "DISPATCH_400_VEHICLE", "차종이 오더와 같고 톤수가 오더 이상인 차량만 요청할 수 있어요."),
	DISPATCH_REQUEST_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "DISPATCH_429_LIMIT", "대기 중인 배차 요청은 3건까지 걸어 둘 수 있어요."),
	DISPATCH_TOO_FAST(HttpStatus.TOO_MANY_REQUESTS, "DISPATCH_429_FAST", "요청이 너무 빨라요. 1분 뒤에 다시 해 주세요."),
	DISPATCH_RESTRICTED(HttpStatus.FORBIDDEN, "DISPATCH_403_RESTRICTED", "늦은 취소가 30일에 3번이라 잠시 배차를 요청할 수 없어요."),
	DISPATCH_ALREADY_REQUESTED(HttpStatus.CONFLICT, "DISPATCH_409_DUPLICATE", "이미 배차를 요청한 오더예요."),
	DISPATCH_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "DISPATCH_409_STATUS", "이미 확정됐거나 만료·철회된 요청이에요."),

	// 운행
	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_404", "운행을 찾을 수 없어요."),
	TRIP_INVALID_FARE(HttpStatus.BAD_REQUEST, "TRIP_400_FARE", "운임은 0원에서 1억 원 사이로 입력해 주세요."),
	TRIP_INVALID_STATUS(HttpStatus.CONFLICT, "TRIP_409_STATUS", "지금 운행 상태에서는 할 수 없는 요청이에요."),
	TRIP_CANCEL_AFTER_LOADING(HttpStatus.CONFLICT, "TRIP_409_LOADED", "짐을 실은 뒤에는 앱에서 취소할 수 없어요. 주선사에게 연락해 주세요."),
	TRIP_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "TRIP_409_IN_PROGRESS", "이미 진행 중인 운행이 있습니다. 먼저 끝내거나 취소해 주세요."),
	TRIP_NO_VEHICLE(HttpStatus.BAD_REQUEST, "TRIP_400_VEHICLE", "운행에 쓸 차량을 먼저 등록해 주세요."),
	TRIP_NOT_DRIVER(HttpStatus.FORBIDDEN, "TRIP_403_ROLE", "차주 계정만 운행을 등록할 수 있어요."),

	// 대기
	WAIT_NOT_FOUND(HttpStatus.NOT_FOUND, "WAIT_404", "대기 기록이 없어요. 상하차지에 진입한 뒤에 할 수 있어요."),
	WAIT_PROOF_NO_BROKER(HttpStatus.CONFLICT, "WAIT_409_NO_BROKER", "이 운행의 주선사는 연락처가 없어 바로 보낼 수 없어요."),
	WAIT_PROOF_NOT_FOUND(HttpStatus.NOT_FOUND, "WAIT_404_PROOF", "증빙을 찾을 수 없거나 링크가 만료됐어요."),
	WAIT_POLICY_FORBIDDEN(HttpStatus.FORBIDDEN, "WAIT_403_POLICY", "주선사 대표만 대기비 정책을 바꿀 수 있어요."),
	WAIT_INVALID_POLICY(HttpStatus.BAD_REQUEST, "WAIT_400_POLICY", "대기비 정책 값이 올바르지 않아요. 단위 시간은 1분 이상, 상한은 단위 금액 이상이어야 해요."),
	WAIT_INVALID_TIME(HttpStatus.BAD_REQUEST, "WAIT_400_TIME", "시각은 진입, 작업 시작, 이탈 순서여야 해요."),
	WAIT_INVALID_STATUS(HttpStatus.CONFLICT, "WAIT_409_STATUS", "지금 대기 상태에서는 할 수 없는 요청이에요."),

	// 인수증
	DELIVERY_PROOF_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY_404", "인수증 사진을 찾을 수 없어요."),
	DELIVERY_NOT_RECEIPT(HttpStatus.CONFLICT, "DELIVERY_409_NOT_RECEIPT", "결제 방식이 인수증인 운행만 인수증을 보내요."),
	DELIVERY_TOO_EARLY(HttpStatus.CONFLICT, "DELIVERY_409_TOO_EARLY", "인수증 사진은 하차지에 도착한 뒤 올릴 수 있어요."),
	DELIVERY_ALREADY_SENT(HttpStatus.CONFLICT, "DELIVERY_409_SENT", "이미 주선사에게 보낸 인수증이에요."),
	DELIVERY_NO_PHOTO(HttpStatus.CONFLICT, "DELIVERY_409_NO_PHOTO", "인수증 사진을 한 장 이상 올려 주세요."),
	DELIVERY_TRIP_CANCELLED(HttpStatus.CONFLICT, "DELIVERY_409_CANCELLED", "취소된 운행은 인수증을 보낼 수 없어요."),
	DELIVERY_INVALID_FILE(HttpStatus.BAD_REQUEST, "DELIVERY_400_FILE", "사진은 JPG·PNG 5MB 이하로 올려 주세요."),
	DELIVERY_PHOTO_LIMIT(HttpStatus.BAD_REQUEST, "DELIVERY_400_PHOTO_LIMIT", "사진은 5장까지 올릴 수 있어요."),

	// 장부
	LEDGER_NOT_FOUND(HttpStatus.NOT_FOUND, "LEDGER_404", "장부 항목을 찾을 수 없어요."),
	LEDGER_INVALID(HttpStatus.BAD_REQUEST, "LEDGER_400", "장부 입력값이 올바르지 않아요."),
	LEDGER_NOT_DRIVER(HttpStatus.FORBIDDEN, "LEDGER_403_ROLE", "차주 계정만 장부를 쓸 수 있어요."),
	LEDGER_AUTO_ENTRY(HttpStatus.CONFLICT, "LEDGER_409_AUTO", "운행으로 자동 생긴 장부는 지울 수 없습니다. 운임이나 결제 방식을 고쳐 주세요."),
	EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPENSE_404", "경비를 찾을 수 없어요."),

	// 자주 쓰는 주소
    SAVED_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "SAVED_ADDRESS_404", "저장한 주소를 찾을 수 없어요."),
    SAVED_ADDRESS_DUPLICATE(HttpStatus.CONFLICT, "SAVED_ADDRESS_409", "이미 저장한 주소예요. 자주 쓰는 주소에서 선택해 주세요."),
    SAVED_ADDRESS_INVALID(HttpStatus.BAD_REQUEST, "SAVED_ADDRESS_400", "주소와 상세주소는 합쳐서 200자 이내로 입력해 주세요. 위치도 확인해 주세요."),

    // 주소 조회
    ADDRESS_INVALID(HttpStatus.BAD_REQUEST, "ADDRESS_400", "검색할 주소를 200자 이내로 입력해 주세요."),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_404", "이 주소의 위치를 찾지 못했어요. 주소를 다시 검색해 주세요."),
    ADDRESS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ADDRESS_503", "위치를 조회하지 못했어요. 잠시 뒤 다시 시도해 주세요."),

    // 상하차지
	SITE_NOT_FOUND(HttpStatus.NOT_FOUND, "SITE_404", "상하차지를 찾을 수 없어요."),
	SITE_NOT_DRIVER(HttpStatus.FORBIDDEN, "SITE_403_ROLE", "차주 계정만 상하차지 정보와 메모를 남길 수 있어요."),
	SITE_INVALID_LOCATION(HttpStatus.BAD_REQUEST, "SITE_400_LOCATION", "위치 좌표가 올바르지 않아요."),
	SITE_INVALID_RADIUS(HttpStatus.BAD_REQUEST, "SITE_400_RADIUS", "도착 판정 반경은 50m에서 1,000m 사이로 정해 주세요."),
	SITE_INVALID_ATTRIBUTE(HttpStatus.BAD_REQUEST, "SITE_400_ATTRIBUTE", "상하차지 정보 값이 올바르지 않아요."),
	SITE_INVALID_NOTE(HttpStatus.BAD_REQUEST, "SITE_400_NOTE", "메모는 1자에서 500자 사이로 남겨 주세요."),

	// 운영자 · 차주 서류 심사
	ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "ADMIN_403_ROLE", "운영자 계정만 쓸 수 있어요."),
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_404", "서류를 찾을 수 없어요."),
	DOCUMENT_NOT_DRIVER(HttpStatus.FORBIDDEN, "DOCUMENT_403_ROLE", "차주 계정만 서류를 낼 수 있어요."),
	DOCUMENT_NOT_BROKER(HttpStatus.FORBIDDEN, "DOCUMENT_403_BROKER", "주선사 담당자 계정만 회사 서류를 낼 수 있어요."),
	DRIVER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "DRIVER_403_UNVERIFIED",
			"서류 심사가 끝나야 배차를 요청할 수 있어요. 내 정보 > 서류에서 4가지 서류를 올려 주세요."),
	BROKER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "BROKER_403_UNVERIFIED",
			"회사 서류 심사가 끝나야 오더를 올릴 수 있습니다. 설정 > 회사 서류에서 사업자등록증과 운송주선사업 허가증을 올려 주세요."),
	DOCUMENT_INVALID_FILE(HttpStatus.BAD_REQUEST, "DOCUMENT_400_FILE", "서류 사진은 JPG·PNG 5MB 이하로 올려 주세요."),
	DOCUMENT_BUSINESS_INFO(HttpStatus.BAD_REQUEST, "DOCUMENT_400_BUSINESS", "사업자등록증의 사업자등록번호 10자리와 개업일을 입력해 주세요."),
	DOCUMENT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "DOCUMENT_400_REASON", "반려 사유를 골라 주세요. 기타는 사유를 직접 적어야 해요."),
	DOCUMENT_PENDING(HttpStatus.CONFLICT, "DOCUMENT_409_PENDING", "심사 중인 서류예요. 결과가 나온 뒤 다시 올릴 수 있어요."),
	DOCUMENT_ALREADY_REVIEWED(HttpStatus.CONFLICT, "DOCUMENT_409_REVIEWED", "다른 운영자가 먼저 처리했어요. 새로 고침해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
