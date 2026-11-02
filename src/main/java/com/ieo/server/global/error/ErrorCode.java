package com.ieo.server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "로그인이 필요합니다."),
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "COMMON_501", "아직 구현되지 않은 기능입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다."),

	// 사용자
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
	USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "USER_403", "이용이 제한되었거나 탈퇴한 계정입니다."),
	USER_INVALID_STATUS(HttpStatus.CONFLICT, "USER_409", "지금 계정 상태에서는 할 수 없는 요청입니다."),

	// 인증
	AUTH_CODE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_RESEND", "인증번호는 1분에 한 번만 받을 수 있습니다."),
	AUTH_CODE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_DAILY", "오늘은 인증번호를 더 받을 수 없습니다."),
	AUTH_CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_LOCKED", "인증번호를 여러 번 틀려 잠시 막혔습니다. 10분 뒤에 다시 시도해 주세요."),
	AUTH_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_400_CODE", "인증번호가 맞지 않습니다."),
	AUTH_CODE_EXPIRED(HttpStatus.GONE, "AUTH_410_CODE", "인증번호가 만료되었습니다. 다시 받아 주세요."),
	AUTH_PHONE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_PHONE", "휴대폰 인증이 만료되었습니다. 다시 인증해 주세요."),
	AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요."),
	AUTH_PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_409_PHONE", "이미 가입된 번호입니다."),
	AUTH_REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "AUTH_400_CONSENT", "필수 약관에 동의해 주세요."),

	// 차량 QR 로그인
	CAR_LOGIN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAR_LOGIN_404", "로그인 요청을 찾을 수 없습니다. 차량 화면의 코드를 확인해 주세요."),
	CAR_LOGIN_EXPIRED(HttpStatus.GONE, "CAR_LOGIN_410", "로그인 요청이 만료되었습니다. 차량 화면의 새 QR을 찍어 주세요."),
	CAR_LOGIN_ALREADY_DECIDED(HttpStatus.CONFLICT, "CAR_LOGIN_409", "이미 승인하거나 거절한 요청입니다."),
	CAR_LOGIN_NOT_APPROVED(HttpStatus.CONFLICT, "CAR_LOGIN_409_NOT_APPROVED", "아직 승인되지 않은 요청입니다."),

	// 차량
	VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "VEHICLE_404", "차량을 찾을 수 없습니다."),
	VEHICLE_INVALID_PLATE(HttpStatus.BAD_REQUEST, "VEHICLE_400_PLATE", "차량 번호 형식이 올바르지 않습니다. 예: 경기82사1234, 123바4567"),
	VEHICLE_INVALID_HEIGHT(HttpStatus.BAD_REQUEST, "VEHICLE_400_HEIGHT", "차량 높이는 1.5m에서 4.2m 사이로 입력해 주세요."),
	VEHICLE_DUPLICATE_PLATE(HttpStatus.CONFLICT, "VEHICLE_409_PLATE", "이미 다른 계정에 등록된 차량 번호입니다. 고객센터로 문의해 주세요."),
	VEHICLE_NOT_DRIVER(HttpStatus.FORBIDDEN, "VEHICLE_403_ROLE", "차주 계정만 차량을 등록할 수 있습니다."),
	VEHICLE_IN_USE(HttpStatus.CONFLICT, "VEHICLE_409_IN_USE", "운행 기록이 있는 차량은 지울 수 없습니다."),
	VEHICLE_VIN_OTHER_OWNER(HttpStatus.CONFLICT, "VEHICLE_409_VIN", "이 차량은 다른 계정에 등록돼 있습니다. 내 차라면 고객센터로 문의해 주세요."),

	// 주선사
	BROKER_NOT_FOUND(HttpStatus.NOT_FOUND, "BROKER_404", "주선사를 찾을 수 없습니다."),

	// 배차
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404", "오더를 찾을 수 없습니다."),
	ORDER_INVALID(HttpStatus.BAD_REQUEST, "ORDER_400", "오더 정보가 올바르지 않습니다."),
	ORDER_NOT_OPEN(HttpStatus.CONFLICT, "ORDER_409_CLOSED", "모집이 끝난 오더입니다."),
	DISPATCH_VEHICLE_MISMATCH(HttpStatus.BAD_REQUEST, "DISPATCH_400_VEHICLE", "오더의 톤수·차종과 차량이 맞지 않습니다."),
	DISPATCH_REQUEST_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "DISPATCH_429_LIMIT", "대기 중인 배차 요청은 3건까지 걸어 둘 수 있습니다."),
	DISPATCH_ALREADY_REQUESTED(HttpStatus.CONFLICT, "DISPATCH_409_DUPLICATE", "이미 배차를 요청한 오더입니다."),
	DISPATCH_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "DISPATCH_409_STATUS", "이미 확정됐거나 만료·철회된 요청입니다."),

	// 운행
	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_404", "운행을 찾을 수 없습니다."),
	TRIP_INVALID_FARE(HttpStatus.BAD_REQUEST, "TRIP_400_FARE", "운임은 0원에서 1억 원 사이로 입력해 주세요."),
	TRIP_INVALID_STATUS(HttpStatus.CONFLICT, "TRIP_409_STATUS", "지금 운행 상태에서는 할 수 없는 요청입니다."),
	TRIP_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "TRIP_409_IN_PROGRESS", "이미 진행 중인 운행이 있습니다. 먼저 끝내거나 취소해 주세요."),
	TRIP_NO_VEHICLE(HttpStatus.BAD_REQUEST, "TRIP_400_VEHICLE", "운행에 쓸 차량을 먼저 등록해 주세요."),
	TRIP_NOT_DRIVER(HttpStatus.FORBIDDEN, "TRIP_403_ROLE", "차주 계정만 운행을 등록할 수 있습니다."),

	// 대기
	WAIT_NOT_FOUND(HttpStatus.NOT_FOUND, "WAIT_404", "대기 기록이 없습니다. 상하차지에 진입한 뒤에 할 수 있습니다."),
	WAIT_PROOF_NOT_FOUND(HttpStatus.NOT_FOUND, "WAIT_404_PROOF", "증빙을 찾을 수 없거나 링크가 만료되었습니다."),
	WAIT_POLICY_FORBIDDEN(HttpStatus.FORBIDDEN, "WAIT_403_POLICY", "주선사 대표만 대기비 정책을 바꿀 수 있습니다."),
	WAIT_INVALID_POLICY(HttpStatus.BAD_REQUEST, "WAIT_400_POLICY", "대기비 정책 값이 올바르지 않습니다. 단위 시간은 1분 이상, 상한은 단위 금액 이상이어야 합니다."),
	WAIT_INVALID_TIME(HttpStatus.BAD_REQUEST, "WAIT_400_TIME", "시각은 진입, 작업 시작, 이탈 순서여야 합니다."),
	WAIT_INVALID_STATUS(HttpStatus.CONFLICT, "WAIT_409_STATUS", "지금 대기 상태에서는 할 수 없는 요청입니다."),

	// 상하차지
	SITE_NOT_FOUND(HttpStatus.NOT_FOUND, "SITE_404", "상하차지를 찾을 수 없습니다."),
	SITE_NOT_DRIVER(HttpStatus.FORBIDDEN, "SITE_403_ROLE", "차주 계정만 상하차지 정보와 메모를 남길 수 있습니다."),
	SITE_INVALID_LOCATION(HttpStatus.BAD_REQUEST, "SITE_400_LOCATION", "위치 좌표가 올바르지 않습니다."),
	SITE_INVALID_RADIUS(HttpStatus.BAD_REQUEST, "SITE_400_RADIUS", "도착 판정 반경은 50m에서 1,000m 사이로 정해 주세요."),
	SITE_INVALID_ATTRIBUTE(HttpStatus.BAD_REQUEST, "SITE_400_ATTRIBUTE", "상하차지 정보 값이 올바르지 않습니다."),
	SITE_INVALID_NOTE(HttpStatus.BAD_REQUEST, "SITE_400_NOTE", "메모는 1자에서 500자 사이로 남겨 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
