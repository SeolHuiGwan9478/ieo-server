package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.ConsentType;
import java.time.Instant;

/** 동의 항목 하나의 지금 상태. 한 번도 고른 적 없으면 agreed는 false, version·decidedAt은 null이다 */
public record ConsentResponse(ConsentType type, String description, boolean required, boolean agreed, String version,
		Instant decidedAt) {
}
