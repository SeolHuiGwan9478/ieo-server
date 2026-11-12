package com.ieo.server.domain.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 휴대폰 현장 정보에서 남긴 메모 (SITE-004, 008). 다른 차주에게는 익명으로 보인다 */
public record CreateSiteNoteRequest(@NotBlank @Size(max = 500) String text) {
}
