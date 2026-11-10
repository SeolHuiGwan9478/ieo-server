package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentStatus;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.enums.RejectReason;
import java.time.Instant;

/** 서류 한 건. rejectMessage는 차주에게 그대로 보이는 문구. businessCheck는 사업자등록증에만 있다 */
public record DocumentResponse(
		Long id,
		DocumentType type,
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

	public static DocumentResponse from(DriverDocument document) {
		RejectReason reason = document.getRejectReason();
		return new DocumentResponse(
				document.getId(),
				document.getType(),
				document.getType().getLabel(),
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
