package com.ieo.server.domain.user.dto;

import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.vehicle.dto.CreateVehicleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 휴대폰 인증 → 약관 동의 → 기본 정보까지 마친 뒤 한 번에 보낸다.
 * vehicle은 선택. 차주만 보낼 수 있고, 보내면 기본 차량으로 등록한다 (모바일 가입 화면에서는 vin을 보내지 않는다).
 */
public record SignUpRequest(
		@NotBlank String phoneToken,
		@NotBlank @Size(max = 100) String deviceId,
		@NotNull UserRole role,
		@NotBlank @Size(max = 30) String name,
		@NotEmpty List<@Valid @NotNull ConsentRequest> consents,
		@Valid CreateVehicleRequest vehicle) {
}
