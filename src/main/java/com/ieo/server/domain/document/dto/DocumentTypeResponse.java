package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.ReviewDocument;
import com.ieo.server.domain.document.enums.DocumentKind;
import java.util.List;

/** 서류 종류 하나의 현재 상태. latest가 없으면 아직 낸 적 없는 서류. history는 최근 제출이 앞 */
public record DocumentTypeResponse(
		String type,
		String label,
		boolean required,
		List<String> checks,
		DocumentResponse latest,
		List<DocumentResponse> history
) {

	public static DocumentTypeResponse of(DocumentKind type, List<? extends ReviewDocument> submissionsLatestFirst) {
		List<DocumentResponse> history = submissionsLatestFirst.stream().map(DocumentResponse::from).toList();
		return new DocumentTypeResponse(type.name(), type.getLabel(), type.isRequired(), type.getChecks(),
				history.isEmpty() ? null : history.get(0), history);
	}
}
