package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentType;
import java.util.List;

/** 서류 종류 하나의 현재 상태. latest가 없으면 아직 낸 적 없는 서류. history는 최근 제출이 앞 */
public record DocumentTypeResponse(
		DocumentType type,
		String label,
		boolean required,
		List<String> checks,
		DocumentResponse latest,
		List<DocumentResponse> history
) {

	public static DocumentTypeResponse of(DocumentType type, List<DriverDocument> submissionsLatestFirst) {
		List<DocumentResponse> history = submissionsLatestFirst.stream().map(DocumentResponse::from).toList();
		return new DocumentTypeResponse(type, type.getLabel(), type.isRequired(), type.getChecks(),
				history.isEmpty() ? null : history.get(0), history);
	}
}
