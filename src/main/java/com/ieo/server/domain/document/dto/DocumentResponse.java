package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.ReviewDocument;
import com.ieo.server.domain.document.enums.DocumentStatus;
import com.ieo.server.domain.document.enums.RejectReason;
import java.time.Instant;

/** 서류 한 건 (차주·주선사 공통). type은 DocumentType 또는 BrokerDocumentType 이름. rejectMessage는 낸 사람에게 그대로 보이는 문구. businessCheck는 사업자등록증에만 있다 */
public record DocumentResponse(
		Long id,
		String type,
		String typeLabel,
		DocumentStatus status,
		String statusLabel,
		Instant submittedAt,
		Instant reviewedAt,
		RejectReason rejectReason,
		String rejectReasonLabel,
		String rejectMessage,
		String rejectNote,
		BusinessCheckResponse businessCheck
) {

	public static DocumentResponse from(ReviewDocument document) {
		RejectReason reason = document.getRejectReason();
		return new DocumentResponse(
				document.getId(),
				document.kind().name(),
				document.kind().getLabel(),
				document.getStatus(),
				document.getStatus().getLabel(),
				document.getCreatedAt(),
				document.getReviewedAt(),
				reason,
				reason == null ? null : reason.getLabel(),
				document.rejectMessage(),
				document.getRejectNote(),
				BusinessCheckResponse.from(document.getBusinessCheck()));
	}
}
