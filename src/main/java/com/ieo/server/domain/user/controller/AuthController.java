package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.PhoneLoginRequest;
import com.ieo.server.domain.user.dto.PhoneVerifyResponse;
import com.ieo.server.domain.user.dto.RefreshTokenRequest;
import com.ieo.server.domain.user.dto.SendCodeRequest;
import com.ieo.server.domain.user.dto.SendCodeResponse;
import com.ieo.server.domain.user.entity.PhoneVerification;
import com.ieo.server.domain.user.dto.SignUpRequest;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.dto.VerifyCodeRequest;
import com.ieo.server.domain.user.service.AuthService;
import com.ieo.server.domain.user.service.PhoneAuthService;
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
 * 휴대폰 로그인·가입. (요구사항: ACC-001)
 * 문자 인증 → phoneToken → 로그인(가입된 번호) 또는 약관·기본 정보 입력 뒤 가입(새 번호).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final PhoneAuthService phoneAuthService;
	private final AuthService authService;
	private final Environment environment;

	/** 인증번호를 보낸다. 같은 번호는 1분에 한 번, 하루 5번까지. */
	@PostMapping("/phone/code")
	public ResponseEntity<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
		String code = phoneAuthService.sendCode(request.phone());
		boolean demo = environment.matchesProfiles("local & !prod & !dev")
				&& environment.getProperty("ieo.auth.show-demo-code", Boolean.class, false);
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

	/** 새 번호면 약관 동의·기본 정보와 함께 가입한다. */
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
