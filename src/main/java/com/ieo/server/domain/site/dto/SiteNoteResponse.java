package com.ieo.server.domain.site.dto;

import com.ieo.server.domain.site.entity.SiteNote;
import java.time.Instant;

/** 작성자는 익명이라 보내지 않고, 내가 쓴 메모인지만 알려 준다 (SITE-008) */
public record SiteNoteResponse(Long id, String text, int helpfulCount, boolean mine, Instant createdAt) {

	public static SiteNoteResponse of(SiteNote note, Long userId) {
		return new SiteNoteResponse(note.getId(), note.getText(), note.getHelpfulCount(),
				note.isWrittenBy(userId), note.getCreatedAt());
	}
}
