package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.RejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 반려. 사유는 필수이고, 기타(OTHER)면 note를 직접 적어야 한다 */
public record RejectRequest(@NotNull RejectReason reasonCode, @Size(max = DriverDocument.MAX_NOTE_LENGTH) String note) {
}
