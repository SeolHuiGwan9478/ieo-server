package com.ieo.server.domain.document.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사업자등록증의 국세청 진위확인 결과 (07 인계서 12장). 차주가 입력한 사업자등록번호·개업일·대표자를 국세청 등록 정보와 맞춰 본다.
 * 결과는 운영자가 참고만 한다. 승인·반려는 사람이 정한다 (반자동).
 */
@Getter
@RequiredArgsConstructor
public enum BusinessCheckResult {
	/** 세 값이 모두 국세청 등록 정보와 같다. 사업자 상태(계속·휴업·폐업)는 BusinessStatus에 따로 둔다 */
	MATCH("일치"),
	/** 등록되지 않은 번호이거나 개업일·대표자가 다르다 */
	MISMATCH("불일치"),
	/** 국세청이 응답하지 않았거나 키가 거부됐다. 운영자가 다시 조회할 수 있다 */
	ERROR("조회 실패"),
	/** API 키가 없어 조회하지 않았다. 운영자가 눈으로만 본다 */
	SKIPPED("조회 안 함");

	private final String label;
}
