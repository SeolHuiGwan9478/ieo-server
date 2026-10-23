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
	AUTH_REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "AUTH_400_CONSENT", "필수 약관에 동의해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
