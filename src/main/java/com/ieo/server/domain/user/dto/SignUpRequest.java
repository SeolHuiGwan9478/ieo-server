package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 휴대폰 인증 → 약관 동의 → 기본 정보까지 마친 뒤 한 번에 보낸다. 차량 정보는 차량 엔티티를 만든 뒤 추가한다. */
public record SignUpRequest(
		@NotBlank String phoneToken,
		@NotBlank @Size(max = 100) String deviceId,
		@NotNull UserRole role,
		@NotBlank @Size(max = 30) String name,
		@NotEmpty List<@Valid @NotNull ConsentRequest> consents) {
}
