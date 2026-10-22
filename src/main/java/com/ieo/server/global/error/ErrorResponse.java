package com.ieo.server.global.error;

import java.util.List;

/**
 * 모든 오류 응답의 공통 형식.
 * fieldErrors는 입력값 검증에 실패했을 때만 채운다.
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

	public record FieldError(String field, String reason) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
	}
}
