package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.PhoneLoginRequest;
import com.ieo.server.domain.user.dto.PhoneVerifyResponse;
import com.ieo.server.domain.user.dto.RefreshTokenRequest;
import com.ieo.server.domain.user.dto.SendCodeRequest;
import com.ieo.server.domain.user.dto.SendCodeResponse;
import com.ieo.server.domain.user.entity.PhoneVerification;
import com.ieo.server.domain.user.dto.SignUpRequest;
import com.ieo.server.domain.user.dto.SocialLoginRequest;
import com.ieo.server.domain.user.dto.SocialVerifyRequest;
import com.ieo.server.domain.user.dto.SocialVerifyResponse;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.dto.VerifyCodeRequest;
import com.ieo.server.domain.user.service.AuthService;
import com.ieo.server.domain.user.service.PhoneAuthService;
import com.ieo.server.domain.user.service.SocialAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴대폰·소셜 로그인과 가입. (요구사항: ACC-001)
 * 문자 인증 → phoneToken, 또는 Google·카카오 확인 → socialToken.
 * 그 토큰으로 로그인(가입된 계정)하거나 약관·기본 정보 입력 뒤 가입(새 계정)한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final PhoneAuthService phoneAuthService;
	private final SocialAuthService socialAuthService;
	private final AuthService authService;
	private final Environment environment;

	/** 인증번호를 보낸다. 같은 번호는 1분에 한 번, 하루 5번까지. */
	@PostMapping("/phone/code")
	public ResponseEntity<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
		String code = phoneAuthService.sendCode(request.phone());
		// 문자 업체를 붙이기 전 시연 기간에는 화면에 번호를 보여 준다 (2026-11-14, 공모전 운영 서버도 켬).
		// 켜면 아무 번호로나 로그인할 수 있으니, 실제 문자를 보내기 시작하면 IEO_AUTH_SHOW_DEMO_CODE를 끈다
		boolean demo = environment.getProperty("ieo.auth.show-demo-code", Boolean.class, false);
		if (demo) {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(new SendCodeResponse(code, PhoneVerification.CODE_TTL.toSeconds()));
		}
		return ResponseEntity.noContent().build();
	}

	/** 인증번호를 확인하고 phoneToken을 준다. 가입된 계정이 있으면 그 정보도 같이 준다. */
	@PostMapping("/phone/verify")
	public PhoneVerifyResponse verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
		return phoneAuthService.verifyCode(request);
	}

	/** 가입된 번호면 phoneToken으로 로그인한다. */
	@PostMapping("/phone/login")
	public TokenResponse loginWithPhone(@Valid @RequestBody PhoneLoginRequest request) {
		return authService.loginWithPhone(request);
	}

	/** Google ID 토큰이나 카카오 인가 코드를 확인하고 socialToken을 준다. 가입된 계정이 있으면 그 정보도 같이 준다. */
	@PostMapping("/social/verify")
	public SocialVerifyResponse verifySocial(@Valid @RequestBody SocialVerifyRequest request) {
		return socialAuthService.verify(request);
	}

	/** 가입된 소셜 계정이면 socialToken으로 로그인한다. */
	@PostMapping("/social/login")
	public TokenResponse loginWithSocial(@Valid @RequestBody SocialLoginRequest request) {
		return authService.loginWithSocial(request);
	}

	/** 새 계정이면 약관 동의·기본 정보와 함께 가입한다. phoneToken 또는 socialToken 중 하나를 보낸다. */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse signUp(@Valid @RequestBody SignUpRequest request) {
		return authService.signUp(request);
	}

	/** 리프레시 토큰으로 토큰을 다시 발급한다. */
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request);
	}
}
