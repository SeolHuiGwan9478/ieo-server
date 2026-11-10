package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.enums.RejectReason;
import com.ieo.server.domain.document.enums.ReviewSubject;
import java.util.List;

/** 반려 창의 선택지. 차주 서류와 주선사 서류의 목록이 다르다 (RejectReason.subjects) */
public record RejectReasonResponse(RejectReason code, String label, String message, boolean noteRequired) {

	public static List<RejectReasonResponse> forSubject(ReviewSubject subject) {
		return RejectReason.forSubject(subject).stream()
				.map(reason -> new RejectReasonResponse(reason, reason.getLabel(), reason.getMessage(), reason.requiresNote()))
				.toList();
	}
}
