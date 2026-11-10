package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.BrokerDocument;
import com.ieo.server.domain.document.enums.BrokerDocumentType;
import com.ieo.server.domain.document.service.DocumentReview;
import java.util.Arrays;
import java.util.List;

/** 주선사 콘솔이 보는 우리 회사 서류. 종류별 최신 상태와 반려 사유. verified면 두 서류가 모두 승인된 인증된 회사 */
public record BrokerDocumentsResponse(boolean verified, int approvedRequired, int requiredTotal, List<DocumentTypeResponse> documents) {

	public static BrokerDocumentsResponse of(DocumentReview<BrokerDocumentType, BrokerDocument> review) {
		List<DocumentTypeResponse> documents = Arrays.stream(BrokerDocumentType.values())
				.map(type -> DocumentTypeResponse.of(type, review.submissions(type)))
				.toList();
		return new BrokerDocumentsResponse(review.verified(), review.approvedRequired(), review.requiredTotal(), documents);
	}
}
