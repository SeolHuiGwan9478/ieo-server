package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.enums.ReviewFilter;
import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;

/** 운영자 심사 목록의 탭·정렬·페이지 규칙. 차주 목록과 주선사 목록이 같이 쓴다 */
final class ReviewListing {

	private static final int MAX_PAGE_SIZE = 100;

	private ReviewListing() {
	}

	static int pageSize(int size) {
		return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
	}

	static int pageNo(int page) {
		return Math.max(0, page);
	}

	static boolean matches(ReviewFilter tab, DocumentReview<?, ?> review) {
		return switch (tab) {
			case PENDING -> review.pendingCount() > 0;
			case APPROVED -> review.verified();
			case REJECTED -> review.rejectedCount() > 0;
			case ALL -> true;
		};
	}

	/** 심사 대기는 오래 기다린 쪽이 앞, 나머지는 최근에 낸 쪽이 앞 */
	static <C> Comparator<C> order(ReviewFilter tab, Function<C, DocumentReview<?, ?>> reviewOf) {
		if (tab == ReviewFilter.PENDING) {
			return Comparator.comparing(candidate -> reviewOf.apply(candidate).oldestPendingAt().orElse(Instant.MAX));
		}
		return Comparator.comparing((C candidate) -> reviewOf.apply(candidate).latestSubmittedAt().orElse(Instant.MIN)).reversed();
	}

	/** 띄어쓰기·하이픈을 빼고 소문자로. 검색어와 비교 대상에 똑같이 쓴다 */
	static String normalize(String value) {
		return value == null ? "" : value.replaceAll("[\\s-]", "").toLowerCase();
	}
}
