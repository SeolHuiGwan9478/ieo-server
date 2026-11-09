package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.driver.dto.BusinessInfoResponse;
import com.ieo.server.domain.driver.dto.UpdateBusinessInfoRequest;
import com.ieo.server.domain.driver.service.BusinessInfoService;
import com.ieo.server.domain.user.dto.CarSessionResponse;
import com.ieo.server.domain.user.dto.ChangeConsentRequest;
import com.ieo.server.domain.user.dto.ConsentResponse;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.service.CarSessionService;
import com.ieo.server.domain.user.service.ConsentService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보의 설정: 로그인된 차량, 동의 관리, 사업자 정보 (ACC-003·005·006) */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountSettingsController {

	private final CarSessionService carSessionService;
	private final ConsentService consentService;
	private final BusinessInfoService businessInfoService;

	@GetMapping("/car-sessions")
	public List<CarSessionResponse> carSessions(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return carSessionService.list(userId);
	}

	/** 휴대폰에서 다른 차량을 로그아웃시킨다 */
	@PostMapping("/car-sessions/{sessionId}/revoke")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revokeCarSession(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable UUID sessionId) {
		carSessionService.revoke(userId, sessionId);
	}

	/** 차량 앱이 스스로 로그아웃할 때. 차량 토큰이 아니면 아무 일도 하지 않는다 */
	@PostMapping("/car-sessions/current/sign-out")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void signOutCurrentCar(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestAttribute(name = AuthInterceptor.CAR_SESSION_ID, required = false) UUID carSessionId) {
		if (carSessionId != null) {
			carSessionService.revoke(userId, carSessionId);
		}
	}

	@GetMapping("/consents")
	public List<ConsentResponse> consents(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return consentService.list(userId);
	}

	@PutMapping("/consents/{type}")
	public ConsentResponse changeConsent(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable ConsentType type,
			@Valid @RequestBody ChangeConsentRequest request) {
		return consentService.change(userId, type, request.agreed());
	}

	@GetMapping("/business")
	public BusinessInfoResponse business(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return businessInfoService.get(userId);
	}

	@PutMapping("/business")
	public BusinessInfoResponse updateBusiness(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@Valid @RequestBody UpdateBusinessInfoRequest request) {
		return businessInfoService.update(userId, request);
	}

	@DeleteMapping("/business")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearBusiness(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		businessInfoService.clear(userId);
	}
}
