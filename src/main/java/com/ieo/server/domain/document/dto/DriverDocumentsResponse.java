package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.service.DocumentReview;
import java.util.Arrays;
import java.util.List;

/** 차주가 보는 내 서류. 종류별 최신 상태와 반려 사유. verified면 배차를 요청할 수 있는 인증된 차주 */
public record DriverDocumentsResponse(boolean verified, int approvedRequired, int requiredTotal, List<DocumentTypeResponse> documents) {

	public static DriverDocumentsResponse of(DocumentReview<DocumentType, DriverDocument> review) {
		List<DocumentTypeResponse> documents = Arrays.stream(DocumentType.values())
				.map(type -> DocumentTypeResponse.of(type, review.submissions(type)))
				.toList();
		return new DriverDocumentsResponse(review.verified(), review.approvedRequired(), review.requiredTotal(), documents);
	}
}
