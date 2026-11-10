package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.BrokerDocumentEvent;
import com.ieo.server.domain.document.entity.DriverDocumentEvent;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.enums.DocumentKind;
import java.time.Instant;

/** 처리 이력 한 줄 (07 인계서 6.5). 차주 서류와 주선사 서류의 이력이 같은 모양으로 나간다 */
public record EventResponse(
		Long id,
		DocumentEventType type,
		String typeLabel,
		Long documentId,
		String documentType,
		String documentTypeLabel,
		Instant at,
		Long actorId,
		String actorName,
		String detail
) {

	public static EventResponse of(DriverDocumentEvent event, String actorName) {
		return of(event.getId(), event.getType(), event.getDocument().getId(), event.getDocument().kind(), event.getCreatedAt(),
				event.getActorUserId(), actorName, event.getDetail());
	}

	public static EventResponse of(BrokerDocumentEvent event, String actorName) {
		return of(event.getId(), event.getType(), event.getDocument().getId(), event.getDocument().kind(), event.getCreatedAt(),
				event.getActorUserId(), actorName, event.getDetail());
	}

	private static EventResponse of(Long id, DocumentEventType type, Long documentId, DocumentKind kind, Instant at, Long actorId, String actorName,
			String detail) {
		return new EventResponse(id, type, type.getLabel(), documentId, kind.name(), kind.getLabel(), at, actorId, actorName, detail);
	}
}
