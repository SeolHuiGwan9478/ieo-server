package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.CarLoginPollResponse;
import com.ieo.server.domain.user.dto.CarLoginRequestResponse;
import com.ieo.server.domain.user.dto.CarLoginSessionResponse;
import com.ieo.server.domain.user.dto.CreateCarLoginSessionRequest;
import com.ieo.server.domain.user.service.CarLoginService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 차량 QR 로그인.
 * /api/auth/car-login/** 는 로그인 전인 차량이 부르고, /api/car-login/** 는 로그인된 휴대폰이 부른다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CarLoginController {

	public static final String POLL_SECRET_HEADER = "X-Poll-Secret";

	private final CarLoginService carLoginService;

	/** 차량: QR 화면을 띄울 때 세션을 만든다. */
	@PostMapping("/auth/car-login/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public CarLoginSessionResponse createSession(
			@Valid @RequestBody CreateCarLoginSessionRequest request, HttpServletRequest httpRequest) {
		return carLoginService.createSession(request, httpRequest.getRemoteAddr());
	}

	/** 차량: 승인됐는지 묻는다. 승인됐으면 토큰을 한 번 받는다. 상태가 바뀌므로 GET이 아니라 POST. */
	@PostMapping("/auth/car-login/sessions/{sessionId}/poll")
	public CarLoginPollResponse poll(
			@PathVariable UUID sessionId,
			@RequestHeader(name = POLL_SECRET_HEADER, required = false) String pollSecret) {
		return carLoginService.poll(sessionId, pollSecret);
	}

	/** 휴대폰: QR을 찍고 승인 화면에 띄울 요청 정보. */
	@GetMapping("/car-login/requests/{code}")
	public CarLoginRequestResponse getRequest(@PathVariable String code) {
		return carLoginService.getRequest(code);
	}

	/** 휴대폰: 승인. 이 계정으로 차량이 로그인된다. */
	@PostMapping("/car-login/requests/{code}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void approve(@PathVariable String code, @RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		carLoginService.approve(code, userId);
	}

	/** 휴대폰: 거절. */
	@PostMapping("/car-login/requests/{code}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@PathVariable String code) {
		carLoginService.reject(code);
	}
}
