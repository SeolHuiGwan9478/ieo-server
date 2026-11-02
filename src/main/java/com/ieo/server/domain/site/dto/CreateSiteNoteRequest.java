package com.ieo.server.domain.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 음성 메모를 받아 적은 텍스트 (SITE-005) */
public record CreateSiteNoteRequest(@NotBlank @Size(max = 500) String text) {
}
